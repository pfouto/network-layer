package pt.unl.fct.di.novasys.channel.simpleclientserver;

import pt.unl.fct.di.novasys.channel.ChannelListener;
import pt.unl.fct.di.novasys.channel.base.SingleThreadedServerChannel;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ClientDownEvent;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ClientUpEvent;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Promise;
import pt.unl.fct.di.novasys.network.AttributeValidator;
import pt.unl.fct.di.novasys.network.Connection;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.NetworkManager;
import pt.unl.fct.di.novasys.network.data.Attributes;
import pt.unl.fct.di.novasys.network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import static pt.unl.fct.di.novasys.channel.simpleclientserver.SimpleClientChannel.SIMPLE_CLIENT_MAGIC_NUMBER;

public class SimpleServerChannel<T> extends SingleThreadedServerChannel<T, T> implements AttributeValidator {

    private static final Logger logger = LogManager.getLogger(SimpleServerChannel.class);


    public final static String NAME = "ServerChannel";
    public final static String ADDRESS_KEY = "address";
    public final static String PORT_KEY = "port";
    public final static String WORKER_GROUP_KEY = "worker_group";
    public final static String HEARTBEAT_INTERVAL_KEY = "heartbeat_interval";
    public final static String HEARTBEAT_TOLERANCE_KEY = "heartbeat_tolerance";
    public final static String CONNECT_TIMEOUT_KEY = "connect_timeout";

    public final static String TRIGGER_SENT_KEY = "trigger_sent";

    public final static String DEFAULT_PORT = "13174";
    public final static String DEFAULT_HB_INTERVAL = "1000";
    public final static String DEFAULT_HB_TOLERANCE = "3000";
    public final static String DEFAULT_CONNECT_TIMEOUT = "1000";

    private final NetworkManager<T> network;
    private final ChannelListener<T> listener;

    private final Map<Host, Connection<T>> clientConnections;

    private final boolean triggerSent;

    public SimpleServerChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties)
            throws UnknownHostException {
        super(NAME);

        this.listener = list;
        this.clientConnections = new HashMap<>();

        InetAddress addr;
        if (properties.containsKey(ADDRESS_KEY))
            addr = Inet4Address.getByName(properties.getProperty(ADDRESS_KEY));
        else
            throw new IllegalArgumentException(NAME + " requires binding address");

        int port = Integer.parseInt(properties.getProperty(PORT_KEY, DEFAULT_PORT));
        int hbInterval = Integer.parseInt(properties.getProperty(HEARTBEAT_INTERVAL_KEY, DEFAULT_HB_INTERVAL));
        int hbTolerance = Integer.parseInt(properties.getProperty(HEARTBEAT_TOLERANCE_KEY, DEFAULT_HB_TOLERANCE));
        int connTimeout = Integer.parseInt(properties.getProperty(CONNECT_TIMEOUT_KEY, DEFAULT_CONNECT_TIMEOUT));
        this.triggerSent = Boolean.parseBoolean(properties.getProperty(TRIGGER_SENT_KEY, "false"));

        Host listenAddress = new Host(addr, port);

        EventLoopGroup eventExecutors = properties.containsKey(WORKER_GROUP_KEY) ?
                (EventLoopGroup) properties.get(WORKER_GROUP_KEY) :
                NetworkManager.createNewWorkerGroup();

        network = new NetworkManager<>(serializer, this, hbInterval, hbTolerance, connTimeout, null);
        network.createServerSocket(this, listenAddress, this, eventExecutors);

    }

    @Override
    protected void onSendMessage(T msg, Host peer, int connection) {
        Connection<T> conn = clientConnections.get(peer);
        if (conn != null) {
            Promise<Void> promise = loop.newPromise();
            promise.addListener(future -> {
                if (future.isSuccess() && triggerSent) listener.messageSent(msg, peer);
                else if(!future.isSuccess()) listener.messageFailed(msg, peer, future.cause());
            });
            conn.sendMessage(msg, promise);
        } else {
            listener.messageFailed(msg, peer, new Exception("No client connection from :" + peer));
        }
    }

    @Override
    protected void onCloseConnection(Host peer, int connection) {
        Connection<T> remove = clientConnections.remove(peer);
        if (remove != null) remove.disconnect();
    }

    @Override
    protected void onInboundConnectionUp(Connection<T> con) {
        logger.debug("Inbound up: " + con);
        clientConnections.put(con.getPeer(), con);
        listener.deliverEvent(new ClientUpEvent(con.getPeer()));
    }

    @Override
    protected void onInboundConnectionDown(Connection<T> con, Throwable cause) {
        logger.debug("Inbound down: " + con + " ... " + cause);
        clientConnections.remove(con.getPeer());
        listener.deliverEvent(new ClientDownEvent(con.getPeer(), cause));
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
    protected void onOpenConnection(Host peer) {
        throw new UnsupportedOperationException("I am Server, not Client");
    }

    @Override
    public boolean validateAttributes(Attributes attr) {
        Short channel = attr.getShort(CHANNEL_MAGIC_ATTRIBUTE);
        return channel != null && channel == SIMPLE_CLIENT_MAGIC_NUMBER;
    }
}
