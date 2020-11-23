package pt.unl.fct.di.novasys.channel.simpleclientserver;

import pt.unl.fct.di.novasys.channel.ChannelListener;
import pt.unl.fct.di.novasys.channel.base.SingleThreadedClientChannel;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Promise;
import pt.unl.fct.di.novasys.network.Connection;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.NetworkManager;
import pt.unl.fct.di.novasys.network.data.Attributes;
import pt.unl.fct.di.novasys.network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ServerDownEvent;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ServerFailedEvent;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ServerUpEvent;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import static pt.unl.fct.di.novasys.network.AttributeValidator.CHANNEL_MAGIC_ATTRIBUTE;

public class SimpleClientChannel<T> extends SingleThreadedClientChannel<T, T> {

    private static final Logger logger = LogManager.getLogger(SimpleClientChannel.class);

    public static final short SIMPLE_CLIENT_MAGIC_NUMBER = 0x5CC5;

    public final static String NAME = "ClientChannel";
    public final static String ADDRESS_KEY = "address";
    public final static String PORT_KEY = "port";
    public final static String WORKER_GROUP_KEY = "workerGroup";
    public final static String HEARTBEAT_INTERVAL_KEY = "heartbeat_interval";
    public final static String HEARTBEAT_TOLERANCE_KEY = "heartbeat_tolerance";
    public final static String CONNECT_TIMEOUT_KEY = "connect_timeout";

    public final static String DEFAULT_PORT = "13174";
    public final static String DEFAULT_HB_INTERVAL = "1000";
    public final static String DEFAULT_HB_TOLERANCE = "3000";
    public final static String DEFAULT_CONNECT_TIMEOUT = "1000";

    private static final Attributes SIMPLE_CLIENT_ATTRIBUTES;

    public enum State {CONNECTING, CONNECTED, DISCONNECTED}

    private State state;

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

        int port = Integer.parseInt(properties.getProperty(PORT_KEY, DEFAULT_PORT));
        int hbInterval = Integer.parseInt(properties.getProperty(HEARTBEAT_INTERVAL_KEY, DEFAULT_HB_INTERVAL));
        int hbTolerance = Integer.parseInt(properties.getProperty(HEARTBEAT_TOLERANCE_KEY, DEFAULT_HB_TOLERANCE));
        int connTimeout = Integer.parseInt(properties.getProperty(CONNECT_TIMEOUT_KEY, DEFAULT_CONNECT_TIMEOUT));

        serverAddress = new Host(addr, port);

        EventLoopGroup eventExecutors = properties.containsKey(WORKER_GROUP_KEY) ?
                (EventLoopGroup) properties.get(WORKER_GROUP_KEY) :
                NetworkManager.createNewWorkerGroup();

        network = new NetworkManager<>(serializer, this, hbInterval, hbTolerance, connTimeout, eventExecutors);

        state = State.DISCONNECTED;
        connection = null;
    }

    @Override
    protected void onOpenConnection(Host peer) {
        connection = network.createConnection(serverAddress, SIMPLE_CLIENT_ATTRIBUTES, this);
        state = State.CONNECTING;
    }

    @Override
    protected void onCloseConnection(Host peer, int conn) {
        connection.disconnect();
        connection = null;
        state = State.DISCONNECTED;
    }

    @Override
    protected void onSendMessage(T msg, Host peer, int conn) {
        if(peer == null) peer = serverAddress;
        if (!peer.equals(serverAddress)) {
            listener.messageFailed(msg, peer, new Exception("Invalid Host"));
            return;
        }

        if (state != State.CONNECTED) {
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
    public void onDeliverMessage(T msg, Connection<T> conn) {
        listener.deliverMessage(msg, conn.getPeer());
    }

    @Override
    protected void onOutboundConnectionUp(Connection<T> conn) {
        connection = conn;
        state = State.CONNECTED;
        logger.debug("Server up: " + conn);
        listener.deliverEvent(new ServerUpEvent(conn.getPeer()));
    }

    @Override
    protected void onOutboundConnectionDown(Connection<T> conn, Throwable cause) {
        connection = null;
        logger.debug("Server down: " + conn + " ... " + cause);
        listener.deliverEvent(new ServerDownEvent(conn.getPeer(), cause));
        connection = null;
        state = State.DISCONNECTED;
    }

    @Override
    protected void onOutboundConnectionFailed(Connection<T> conn, Throwable cause) {
        logger.debug("Server con failed: " + conn + " ... " + cause);
        connection = null;
        listener.deliverEvent(new ServerFailedEvent(conn.getPeer(), cause));
        connection = null;
        state = State.DISCONNECTED;
    }

}
