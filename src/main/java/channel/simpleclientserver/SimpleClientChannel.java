package channel.simpleclientserver;

import channel.ChannelListener;
import channel.base.SingleThreadedClientChannel;
import channel.simpleclientserver.events.*;
import io.netty.util.concurrent.Promise;
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
import java.util.Properties;

import static network.AttributeValidator.CHANNEL_MAGIC_ATTRIBUTE;

public class SimpleClientChannel<T> extends SingleThreadedClientChannel<T, T> {

    private static final Logger logger = LogManager.getLogger(SimpleClientChannel.class);
    public static final short SIMPLE_CLIENT_MAGIC_NUMBER = 0x5CC5;

    private static final Attributes SIMPLE_CLIENT_ATTRIBUTES;

    static {
        SIMPLE_CLIENT_ATTRIBUTES = new Attributes();
        SIMPLE_CLIENT_ATTRIBUTES.putShort(CHANNEL_MAGIC_ATTRIBUTE, SIMPLE_CLIENT_MAGIC_NUMBER);
    }

    public final static int DEFAULT_PORT = 13174;

    private final NetworkManager<T> network;
    private final ChannelListener<T> listener;
    private final Host serverAddress;

    private Connection<T> connection;

    public SimpleClientChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties)
            throws UnknownHostException {
        super("SimpleClientChannel");

        this.listener = list;

        InetAddress addr = null;
        if (properties.containsKey("address"))
            addr = Inet4Address.getByName(properties.getProperty("address"));

        int port = DEFAULT_PORT;
        if (properties.containsKey("port"))
            port = Integer.parseInt(properties.getProperty("port"));

        serverAddress = new Host(addr, port);

        network = new NetworkManager<>(serializer, this,
                1000, 3000, 1000);

        connection = null;
        network.createConnection(serverAddress, SIMPLE_CLIENT_ATTRIBUTES, this);
    }

    @Override
    protected void onSendMessage(T msg, Host peer, int mode) {
        if (peer != null && peer != serverAddress) {
            listener.messageFailed(msg, peer, new Exception("Invalid Host"));
            return;
        }

        if (connection == null) {
            listener.messageFailed(msg, peer, new Exception("Connection not established"));
            return;
        }

        Promise<Void> promise = loop.newPromise();
        promise.addListener(future -> {
            if (!future.isSuccess())
                listener.messageFailed(msg, peer, future.cause());
            else
                listener.messageSent(msg, peer);
        });
        connection.sendMessage(msg, promise);
    }

    @Override
    protected void onCloseConnection(Host peer) {
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
}
