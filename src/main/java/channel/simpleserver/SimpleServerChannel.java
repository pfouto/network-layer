package channel.simpleserver;

import channel.ChannelListener;
import channel.SingleThreadedServerChannel;
import network.AttributeValidator;
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

public class SimpleServerChannel<T> extends SingleThreadedServerChannel<T, T> implements AttributeValidator {

    private static final Logger logger = LogManager.getLogger(SimpleServerChannel.class);
    private static final short SIMPLE_SERVER_MAGIC_NUMBER = 0x55CC;

    private static final Attributes SIMPLE_SERVER_ATTRIBUTES;
    static {
        SIMPLE_SERVER_ATTRIBUTES = new Attributes();
        SIMPLE_SERVER_ATTRIBUTES.putShort("channel", SIMPLE_SERVER_MAGIC_NUMBER);
    }

    public final static int DEFAULT_PORT = 13174;

    private final NetworkManager<T> network;
    private final ChannelListener<T> listener;

    public SimpleServerChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties)
            throws UnknownHostException {

        this.listener = list;

        InetAddress addr = null;
        if(properties.containsKey("address"))
            addr = Inet4Address.getByName(properties.getProperty("address"));

        int port = DEFAULT_PORT;
        if(properties.containsKey("port"))
            port = Integer.parseInt(properties.getProperty("port"));

        network = new NetworkManager<>(serializer, this,
                1000, 3000, 1000);

        if(addr != null)
            network.createServerSocket(this, new Host(addr, port), this);

    }

    @Override
    protected void onSendMessage(T msg, Host peer) {
        ConnectionContext<T> context = establishedConnections.get(peer);
        if (context != null) {
            context.sendMessage(msg);
        } else {
            Pair<Connection<AckosMessage<T>>, Queue<T>> pair = pendingConnections.computeIfAbsent(peer, k ->
                    Pair.of(network.createConnection(peer, SIMPLE_SERVER_ATTRIBUTES), new LinkedList<>()));
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
    protected void onInboundConnectionUp(Connection<T> con) {
        logger.debug("Inbound up: " + con);
    }

    @Override
    protected void onInboundConnectionDown(Connection<T> con, Throwable cause) {
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
    public void onDeliverMessage(T msg, Connection<T> conn) {
        listener.deliverMessage(msg, conn.getPeer());
    }

    @Override
    public boolean validateAttributes(Attributes attr) {
        Short channel = attr.getShort("channel");
        return channel != null && channel == SIMPLE_SERVER_MAGIC_NUMBER;
    }
}
