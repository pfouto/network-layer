package channel.simpleclientserver;

import channel.ChannelListener;
import channel.base.SingleThreadedClientChannel;
import channel.simpleclientserver.events.*;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Promise;
import network.Connection;
import network.ISerializer;
import network.NetworkManager;
import network.data.Attributes;
import network.data.Host;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import static network.AttributeValidator.CHANNEL_MAGIC_ATTRIBUTE;

public class SimpleClientChannel<T> extends SingleThreadedClientChannel<T, T> {

    private static final Logger logger = LogManager.getLogger(SimpleClientChannel.class);

    public static final short SIMPLE_CLIENT_MAGIC_NUMBER = 0x5CC5;
    public final static int DEFAULT_PORT = 13174;

    public final static String NAME = "ClientChannel";
    public final static String ADDRESS_KEY = "address";
    public final static String PORT_KEY = "port";
    public final static String WORKER_GROUP_KEY = "workerGroup";

    private static final Attributes SIMPLE_CLIENT_ATTRIBUTES;

    static {
        SIMPLE_CLIENT_ATTRIBUTES = new Attributes();
        SIMPLE_CLIENT_ATTRIBUTES.putShort(CHANNEL_MAGIC_ATTRIBUTE, SIMPLE_CLIENT_MAGIC_NUMBER);
    }

    private final NetworkManager<T> network;
    private final ChannelListener<T> listener;
    private final Host serverAddress;

    private Connection<T> connection;

    public SimpleClientChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties)
            throws UnknownHostException {
        super(NAME);

        this.listener = list;

        InetAddress addr;
        if (properties.containsKey(ADDRESS_KEY))
            addr = Inet4Address.getByName(properties.getProperty(ADDRESS_KEY));
        else
            throw new IllegalArgumentException(NAME + " requires server address");

        int port = properties.containsKey(PORT_KEY) ? (Integer)(properties.get(PORT_KEY)) : DEFAULT_PORT;

        serverAddress = new Host(addr, port);

        if (properties.containsKey(WORKER_GROUP_KEY))
            network = new NetworkManager<>(serializer, this, 1000, 3000, 1000,
                    (EventLoopGroup) properties.get(WORKER_GROUP_KEY));
        else
            network = new NetworkManager<>(serializer, this, 1000, 3000, 1000);

        connection = null;
        network.createConnection(serverAddress, SIMPLE_CLIENT_ATTRIBUTES, this);
    }

    @Override
    protected void onSendMessage(T msg, Host peer, int conn) {
        if(peer == null) peer = serverAddress;
        if (!peer.equals(serverAddress)) {
            listener.messageFailed(msg, peer, new Exception("Invalid Host"));
            return;
        }

        if (connection == null) {
            listener.messageFailed(msg, peer, new Exception("Connection not established"));
            return;
        }

        Promise<Void> promise = loop.newPromise();
        Host finalPeer = peer;
        promise.addListener(future -> {
            if (!future.isSuccess())
                listener.messageFailed(msg, finalPeer, future.cause());
            else
                listener.messageSent(msg, finalPeer);
        });
        connection.sendMessage(msg, promise);
    }

    @Override
    protected void onCloseConnection(Host peer, int connection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onDeliverMessage(T msg, Connection<T> conn) {
        listener.deliverMessage(msg, conn.getPeer());
    }

    @Override
    protected void onOutboundConnectionUp(Connection<T> conn) {
        connection = conn;
        logger.debug("Server up: " + conn);
        listener.deliverEvent(new ServerUpEvent(conn.getPeer()));
    }

    @Override
    protected void onOutboundConnectionDown(Connection<T> conn, Throwable cause) {
        connection = null;
        logger.debug("Server down: " + conn + " ... " + cause);
        listener.deliverEvent(new ServerDownEvent(conn.getPeer(), cause));
    }

    @Override
    protected void onOutboundConnectionFailed(Connection<T> conn, Throwable cause) {
        logger.debug("Server con failed: " + conn + " ... " + cause);
        connection = null;
        listener.deliverEvent(new ServerFailedEvent(conn.getPeer(), cause));
    }

    @Override
    protected void onOpenConnection(Host peer) {
        throw new NotImplementedException("Pls fix me");
    }
}
