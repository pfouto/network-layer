package channel.simpleclientserver;

import channel.ChannelListener;
import channel.base.SingleThreadedServerChannel;
import channel.simpleclientserver.events.ClientDownEvent;
import channel.simpleclientserver.events.ClientUpEvent;
import channel.tcp.ConnectionState;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Promise;
import network.AttributeValidator;
import network.Connection;
import network.ISerializer;
import network.NetworkManager;
import network.data.Attributes;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.JsonUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static channel.simpleclientserver.SimpleClientChannel.SIMPLE_CLIENT_MAGIC_NUMBER;

public class SimpleServerChannel<T> extends SingleThreadedServerChannel<T, T> implements AttributeValidator {

    private static final Logger logger = LogManager.getLogger(SimpleServerChannel.class);

    public final static int DEFAULT_PORT = 13174;

    public final static String NAME = "ServerChannel";
    public final static String ADDRESS_KEY = "address";
    public final static String PORT_KEY = "port";
    public final static String WORKER_GROUP_KEY = "worker_group";

    public final static String TRIGGER_SENT_KEY = "trigger_sent";
    public final static String DEBUG_INTERVAL_KEY = "debug_interval";

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

        int port = properties.containsKey(PORT_KEY) ? (Integer)(properties.get(PORT_KEY)) : DEFAULT_PORT;

        if (properties.containsKey(WORKER_GROUP_KEY)) {
            network = new NetworkManager<>(serializer, this, 1000, 3000, 1000, null);
            network.createServerSocket(this, new Host(addr, port), this,
                    (EventLoopGroup) properties.get(WORKER_GROUP_KEY));
        } else {
            network = new NetworkManager<>(serializer, this, 1000, 3000, 1000, null);
            network.createServerSocket(this, new Host(addr, port), this);
        }

        triggerSent = Boolean.parseBoolean(properties.getProperty(TRIGGER_SENT_KEY, "false"));

        if(properties.containsKey(DEBUG_INTERVAL_KEY)) {
            int debugInterval = (Integer) (properties.get(DEBUG_INTERVAL_KEY));
            loop.scheduleAtFixedRate(this::print, debugInterval, debugInterval, TimeUnit.MILLISECONDS);
        }

    }

    void print() {
        StringBuilder data = new StringBuilder(getData());
        try {
            data.append("\t");
            long totalRec = 0;
            long totalSent = 0;
            for (Map.Entry<Host, Connection<T>> e : clientConnections.entrySet()) {
                totalRec += e.getValue().getReceivedAppBytes();
                totalSent += e.getValue().getSentAppBytes();
            }
            data.append(clientConnections.size()).append(":").append(String.format("%,d", totalSent))
                    .append("/").append(String.format("%,d", totalRec));
        } catch (Exception e){
            e.printStackTrace();
        }
        logger.info(data);
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
