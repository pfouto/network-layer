package pt.unl.fct.di.novasys.channel.ackos;

import pt.unl.fct.di.novasys.channel.ChannelListener;
import pt.unl.fct.di.novasys.channel.base.SingleThreadedBiChannel;
import pt.unl.fct.di.novasys.channel.ackos.events.NodeDownEvent;
import pt.unl.fct.di.novasys.channel.ackos.messaging.AckosAckMessage;
import pt.unl.fct.di.novasys.channel.ackos.messaging.AckosAppMessage;
import pt.unl.fct.di.novasys.channel.ackos.messaging.AckosMessage;
import pt.unl.fct.di.novasys.channel.ackos.messaging.AckosMessageSerializer;
import io.netty.util.concurrent.Promise;
import pt.unl.fct.di.novasys.network.AttributeValidator;
import pt.unl.fct.di.novasys.network.Connection;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.NetworkManager;
import pt.unl.fct.di.novasys.network.data.Attributes;
import pt.unl.fct.di.novasys.network.data.Host;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

public class AckosChannel<T> extends SingleThreadedBiChannel<T, AckosMessage<T>> implements AttributeValidator {

    private static final Logger logger = LogManager.getLogger(AckosChannel.class);
    private static final short ACKOS_MAGIC_NUMBER = 0x4505;

    private static final Attributes ACKOS_ATTRIBUTES;

    static {
        ACKOS_ATTRIBUTES = new Attributes();
        ACKOS_ATTRIBUTES.putShort(CHANNEL_MAGIC_ATTRIBUTE, ACKOS_MAGIC_NUMBER);
    }

    public final static int DEFAULT_PORT = 13174;

    private final NetworkManager<AckosMessage<T>> network;
    private final ChannelListener<T> listener;

    private Map<Host, Pair<Connection<AckosMessage<T>>, Queue<T>>> pendingConnections;
    private Map<Host, OutConnectionContext<T>> establishedConnections;

    public AckosChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties)
            throws UnknownHostException {
        super("AckosChannel");
        this.listener = list;

        InetAddress addr = null;
        if (properties.containsKey("address"))
            addr = Inet4Address.getByName(properties.getProperty("address"));

        int port = DEFAULT_PORT;
        if (properties.containsKey("port"))
            port = Integer.parseInt(properties.getProperty("port"));

        AckosMessageSerializer<T> tAckosMessageSerializer = new AckosMessageSerializer<>(serializer);
        network = new NetworkManager<>(tAckosMessageSerializer, this,
                1000, 3000, 1000);

        if (addr != null)
            network.createServerSocket(this, new Host(addr, port), this);

        pendingConnections = new HashMap<>();
        establishedConnections = new HashMap<>();
    }

    @Override
    protected void onSendMessage(T msg, Host peer, int connection) {

        OutConnectionContext<T> context = establishedConnections.get(peer);
        if (context != null) {
            Promise<Void> promise = loop.newPromise();
            promise.addListener(future -> {
                if (!future.isSuccess()) listener.messageFailed(msg, peer, future.cause());
            });
            context.sendMessage(msg, promise);
        } else {
            Pair<Connection<AckosMessage<T>>, Queue<T>> pair = pendingConnections.computeIfAbsent(peer, k ->
                    Pair.of(network.createConnection(peer, ACKOS_ATTRIBUTES, this), new LinkedList<>()));
            pair.getValue().add(msg);
        }
    }

    @Override
    protected void onCloseConnection(Host peer, int connection) {
        Pair<Connection<AckosMessage<T>>, Queue<T>> remove = pendingConnections.remove(peer);
        if (remove != null) remove.getKey().disconnect();

        OutConnectionContext<T> context = establishedConnections.get(peer);
        if (context != null) context.getConnection().disconnect();
    }

    @Override
    protected void onOutboundConnectionUp(Connection<AckosMessage<T>> conn) {
        Pair<Connection<AckosMessage<T>>, Queue<T>> remove = pendingConnections.remove(conn.getPeer());
        if (remove != null) {
            logger.debug("Outbound established: " + conn);
            OutConnectionContext<T> ctx = new OutConnectionContext<>(conn);
            OutConnectionContext<T> put = establishedConnections.put(conn.getPeer(), ctx);
            if (put != null) throw new RuntimeException("Context exists in connection up");

            for (T t : remove.getValue()) {
                Promise<Void> promise = loop.newPromise();
                promise.addListener(future -> {
                    if (!future.isSuccess()) listener.messageFailed(t, conn.getPeer(), future.cause());
                });
                ctx.sendMessage(t, promise);
            }
        } else {
            logger.warn("ConnectionUp with no pending: " + conn);
        }
    }

    @Override
    protected void onOutboundConnectionDown(Connection<AckosMessage<T>> conn, Throwable cause) {
        OutConnectionContext<T> context = establishedConnections.remove(conn.getPeer());
        if (context != null) {
            List<T> failed = context.getPending().stream().map(Pair::getValue).collect(Collectors.toList());
            listener.deliverEvent(new NodeDownEvent<>(conn.getPeer(), failed, cause));
        } else {
            logger.warn("ConnectionDown with no context available: " + conn);
        }
    }

    @Override
    protected void onOutboundConnectionFailed(Connection<AckosMessage<T>> conn, Throwable cause) {
        if (establishedConnections.containsKey(conn.getPeer()))
            throw new RuntimeException("Context exists in conn failed");

        Pair<Connection<AckosMessage<T>>, Queue<T>> remove = pendingConnections.remove(conn.getPeer());
        if (remove != null) {
            List<T> failed = new LinkedList<>(remove.getRight());
            listener.deliverEvent(new NodeDownEvent<>(conn.getPeer(), failed, cause));
        } else {
            logger.warn("ConnectionFailed with no pending: " + conn);
        }
    }

    private void handleAckMessage(AckosAckMessage<T> msg, Connection<AckosMessage<T>> conn) {
        if (conn.isInbound()) throw new RuntimeException("Received AckMessage in inbound connection");
        OutConnectionContext<T> context = establishedConnections.get(conn.getPeer());
        if (context == null) throw new RuntimeException("Received AckMessage without an established connection");
        T ackMsg = context.ack(msg.getId());
        listener.messageSent(ackMsg, conn.getPeer());
    }

    private void handleAppMessage(AckosAppMessage<T> msg, Connection<AckosMessage<T>> conn) {
        if (conn.isOutbound()) throw new RuntimeException("Received AppMessage in outbound connection");
        conn.sendMessage(new AckosAckMessage<>(msg.getId()));
        listener.deliverMessage(msg.getPayload(), conn.getPeer());
    }

    @Override
    protected void onInboundConnectionUp(Connection<AckosMessage<T>> con) {
        logger.debug("Inbound up: " + con);
    }

    @Override
    protected void onInboundConnectionDown(Connection<AckosMessage<T>> con, Throwable cause) {
        logger.debug("Inbound down: " + con + " ... " + cause);
    }

    @Override
    public void onServerSocketBind(boolean success, Throwable cause) {
        if (success)
            logger.debug("Server socket ready");
        else
            logger.error("Server socket bind failed: " + cause);
    }

    @Override
    public void onServerSocketClose(boolean success, Throwable cause) {
        logger.debug("Server socket closed. " + (success ? "" : "Cause: " + cause));
    }

    @Override
    public void onDeliverMessage(AckosMessage<T> msg, Connection<AckosMessage<T>> conn) {
        switch (msg.getType()) {
            case ACK:
                handleAckMessage((AckosAckMessage<T>) msg, conn);
                break;
            case APP_MSG:
                handleAppMessage((AckosAppMessage<T>) msg, conn);
                break;
        }
    }

    @Override
    protected void onOpenConnection(Host peer) {
        throw new NotImplementedException("Pls fix me");
    }

    @Override
    public boolean validateAttributes(Attributes attr) {
        Short channel = attr.getShort(CHANNEL_MAGIC_ATTRIBUTE);
        return channel != null && channel == ACKOS_MAGIC_NUMBER;
    }
}
