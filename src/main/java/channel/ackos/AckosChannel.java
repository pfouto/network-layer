package channel.ackos;

import channel.ChannelListener;
import channel.SingleThreadedChannel;
import channel.ackos.events.MessageAckEvent;
import channel.ackos.events.NodeDownEvent;
import channel.ackos.messaging.AckosAckMessage;
import channel.ackos.messaging.AckosAppMessage;
import channel.ackos.messaging.AckosMessage;
import channel.ackos.messaging.AckosMessageSerializer;
import network.Connection;
import network.ISerializer;
import network.NetworkManager;
import network.data.Attributes;
import network.data.Host;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

public class AckosChannel<T> extends SingleThreadedChannel<T, AckosMessage<T>> {

    private static final Logger logger = LogManager.getLogger(AckosChannel.class);

    public final static int DEFAULT_PORT = 13174;

    private final NetworkManager<AckosMessage<T>> network;
    private final ChannelListener<T> listener;

    private Map<Host, Pair<Connection<AckosMessage<T>>, Queue<T>>> pendingConnections;
    private Map<Host, ConnectionContext<T>> establishedConnections;

    public AckosChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties)
            throws UnknownHostException {

        this.listener = list;

        InetAddress addr = null;
        if(properties.containsKey("address"))
            addr = Inet4Address.getByName(properties.getProperty("address"));

        int port = DEFAULT_PORT;
        if(properties.containsKey("port"))
            port = Integer.parseInt(properties.getProperty("port"));

        AckosMessageSerializer<T> tAckosMessageSerializer = new AckosMessageSerializer<>(serializer);
        network = new NetworkManager<>(tAckosMessageSerializer, this, this,
                1000, 3000, 1000);

        if(addr != null)
            network.createServerSocket(this, new Host(addr, port));

        pendingConnections = new HashMap<>();
        establishedConnections = new HashMap<>();
    }

    @Override
    protected void onSendMessage(T msg, Host peer) {
        ConnectionContext<T> context = establishedConnections.get(peer);
        if (context != null) {
            context.sendMessage(msg);
        } else {
            Pair<Connection<AckosMessage<T>>, Queue<T>> pair = pendingConnections.computeIfAbsent(peer, k ->
                    Pair.of(network.createConnection(peer, Attributes.EMPTY), new LinkedList<>()));
            pair.getValue().add(msg);
        }
    }

    @Override
    protected void onCloseConnection(Host peer) {
        Pair<Connection<AckosMessage<T>>, Queue<T>> remove = pendingConnections.remove(peer);
        if(remove != null) remove.getKey().disconnect();

        ConnectionContext<T> context = establishedConnections.remove(peer);
        if(context != null) context.getConnection().disconnect();
    }

    @Override
    protected void onOutboundConnectionUp(Connection<AckosMessage<T>> conn) {
        Pair<Connection<AckosMessage<T>>, Queue<T>> remove = pendingConnections.remove(conn.getPeer());
        if(remove == null) throw new RuntimeException("Pending null in connection up");
        logger.debug("Outbound established: " + conn);
        ConnectionContext<T> ctx = new ConnectionContext<>(conn);
        ConnectionContext<T> put = establishedConnections.put(conn.getPeer(), ctx);
        if(put != null) throw new RuntimeException("Context exists in connection up");

        remove.getValue().forEach(ctx::sendMessage);
    }

    @Override
    protected void onOutboundConnectionDown(Connection<AckosMessage<T>> conn, Throwable cause) {
        ConnectionContext<T> context = establishedConnections.remove(conn.getPeer());
        if(context == null) throw new RuntimeException("Connection down with no context available");

        List<T> failed = context.getPending().stream().map(Pair::getValue).collect(Collectors.toList());

        listener.deliverEvent(new NodeDownEvent<>(conn.getPeer(), failed, cause));
    }

    @Override
    protected void onOutboundConnectionFailed(Connection<AckosMessage<T>> conn, Throwable cause) {
        if(establishedConnections.containsKey(conn.getPeer()))
            throw new RuntimeException("Context exists in conn failed");

        Pair<Connection<AckosMessage<T>>, Queue<T>> remove = pendingConnections.remove(conn.getPeer());
        if(remove == null) throw new RuntimeException("Connection failed with no pending");

        List<T> failed = new LinkedList<>(remove.getRight());
        listener.deliverEvent(new NodeDownEvent<>(conn.getPeer(), failed, cause));
    }

    private void handleAckMessage(AckosAckMessage<T> msg, Connection<AckosMessage<T>> conn) {
        if(conn.isInbound()) throw new RuntimeException("Received AckMessage in inbound connection");
        ConnectionContext<T> context = establishedConnections.get(conn.getPeer());
        if(context == null) throw new RuntimeException("Received AckMessage without an established connection");
        T ackMsg = context.ack(msg.getId());
        listener.deliverEvent(new MessageAckEvent<>(conn.getPeer(), ackMsg));
    }

    private void handleAppMessage(AckosAppMessage<T> msg, Connection<AckosMessage<T>> conn) {
        if(conn.isOutbound()) throw new RuntimeException("Received AppMessage in outbound connection");
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

}
