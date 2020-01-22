package channel.simpleclientserver;

import channel.ChannelListener;
import channel.base.SingleThreadedServerChannel;
import channel.simpleclientserver.events.ClientDownEvent;
import channel.simpleclientserver.events.ClientUpEvent;
import io.netty.util.concurrent.Promise;
import network.AttributeValidator;
import network.Connection;
import network.ISerializer;
import network.NetworkManager;
import network.data.Attributes;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import static channel.simpleclientserver.SimpleClientChannel.SIMPLE_CLIENT_MAGIC_NUMBER;

public class SimpleServerChannel<T> extends SingleThreadedServerChannel<T, T> implements AttributeValidator {

    private static final Logger logger = LogManager.getLogger(SimpleServerChannel.class);

    public final static int DEFAULT_PORT = 13174;

    private final NetworkManager<T> network;
    private final ChannelListener<T> listener;

    private final Map<Host, Connection<T>> clientConnections;


    public SimpleServerChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties)
            throws UnknownHostException {
        super("SimpleServerChannel");

        this.listener = list;
        this.clientConnections = new HashMap<>();

        InetAddress addr = null;
        if (properties.containsKey("address"))
            addr = Inet4Address.getByName(properties.getProperty("address"));

        int port = DEFAULT_PORT;
        if (properties.containsKey("port"))
            port = Integer.parseInt(properties.getProperty("port"));

        network = new NetworkManager<>(serializer, this,
                1000, 3000, 1000);

        if (addr != null)
            network.createServerSocket(this, new Host(addr, port), this);

    }

    @Override
    protected void onSendMessage(T msg, Host peer) {
        Connection<T> conn = clientConnections.get(peer);
        if (conn != null) {
            Promise<Void> promise = loop.newPromise();
            promise.addListener(future -> {
                if (!future.isSuccess())
                    listener.messageFailed(msg, peer, future.cause());
                else
                    listener.messageSent(msg, peer);
            });
            conn.sendMessage(msg, promise);
        } else {
            listener.messageFailed(msg, peer, new Exception("No client connection from :" + peer));
        }
    }

    @Override
    protected void onCloseConnection(Host peer) {
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
    public boolean validateAttributes(Attributes attr) {
        Short channel = attr.getShort("channel");
        return channel != null && channel == SIMPLE_CLIENT_MAGIC_NUMBER;
    }
}
