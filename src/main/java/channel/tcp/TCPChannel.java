package channel.tcp;

import channel.ChannelListener;
import channel.base.SingleThreadedBiChannel;
import channel.tcp.events.*;
import io.netty.util.concurrent.Promise;
import network.AttributeValidator;
import network.Connection;
import network.ISerializer;
import network.NetworkManager;
import network.data.Attributes;
import network.data.Host;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.*;

public class TCPChannel<T> extends SingleThreadedBiChannel<T, T> implements AttributeValidator {

    private static final Logger logger = LogManager.getLogger(TCPChannel.class);
    private static final short TCP_MAGIC_NUMBER = 0x4505;

    public static final String LISTEN_ADDRESS_ATTRIBUTE = "listen_address";

    public final static int DEFAULT_PORT = 85739;
    public final static int CONNECTION_OUT = 0;
    public final static int CONNECTION_IN = 1;

    private final NetworkManager<T> network;
    private final ChannelListener<T> listener;

    private Attributes attributes;

    private Map<Host, Pair<Connection<T>, Queue<T>>> pendingOut;
    private Map<Host, Connection<T>> establishedOut;

    //Host represents the client server socket, not the client tcp connection address!
    //client connection address is in connection.getPeer
    private BidiMap<Host, Connection<T>> establishedIn;

    public TCPChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties)
            throws IOException {
        super("TCPChannel");
        this.listener = list;

        InetAddress addr = null;
        if (properties.containsKey("address"))
            addr = Inet4Address.getByName(properties.getProperty("address"));

        if (addr == null)
            throw new AssertionError("No address received in TCP Channel properties");

        int port = DEFAULT_PORT;
        if (properties.containsKey("port"))
            port = Integer.parseInt(properties.getProperty("port"));

        network = new NetworkManager<>(serializer, this, 1000, 3000, 1000);

        Host listenAddress = new Host(addr, port);
        network.createServerSocket(this, listenAddress, this);

        attributes = new Attributes();
        attributes.putShort(CHANNEL_MAGIC_ATTRIBUTE, TCP_MAGIC_NUMBER);
        attributes.putHost(LISTEN_ADDRESS_ATTRIBUTE, listenAddress);

        pendingOut = new HashMap<>();
        establishedOut = new HashMap<>();
        establishedIn = new DualHashBidiMap<>();
    }

    @Override
    protected void onOpenConnection(Host peer) {
        throw new NotImplementedException("Pls fix me");
    }


    @Override
    protected void onSendMessage(T msg, Host peer, int connection) {
        logger.debug("SendMessage " + msg + " " + peer + " " + (connection == CONNECTION_IN ? "IN" : "OUT"));
        if (connection <= CONNECTION_OUT) {
            Connection<T> established = establishedOut.get(peer);
            if (established != null) {
                sendWithListener(msg, peer, established);
            } else {
                Pair<Connection<T>, Queue<T>> pair = pendingOut.computeIfAbsent(peer, k ->
                        Pair.of(network.createConnection(peer, attributes, this), new LinkedList<>()));
                pair.getValue().add(msg);
            }
        } else if (connection == CONNECTION_IN) {
            Connection<T> inConn = establishedIn.get(peer);
            if (inConn != null)
                sendWithListener(msg, peer, inConn);
            else
                listener.messageFailed(msg, peer, new IllegalArgumentException("No incoming connection"));
        } else {
            listener.messageFailed(msg, peer, new IllegalArgumentException("Invalid send channel: " + connection));
            logger.error("Invalid sendMessage mode " + connection);
        }
    }

    private void sendWithListener(T msg, Host peer, Connection<T> established) {
        Promise<Void> promise = loop.newPromise();
        promise.addListener(future -> {
            if (future.isSuccess()) listener.messageSent(msg, peer);
            else listener.messageFailed(msg, peer, future.cause());
        });
        established.sendMessage(msg, promise);
    }

    @Override
    protected void onOutboundConnectionUp(Connection<T> conn) {
        logger.debug("OutboundConnectionUp " + conn.getPeer());
        Pair<Connection<T>, Queue<T>> remove = pendingOut.remove(conn.getPeer());
        if (remove == null) throw new RuntimeException("Pending null in connection up");
        Connection<T> put = establishedOut.put(conn.getPeer(), conn);
        if (put != null) throw new RuntimeException("Connection already exists in connection up");
        listener.deliverEvent(new OutConnectionUp(conn.getPeer()));
        remove.getValue().forEach(t -> sendWithListener(t, conn.getPeer(), conn));
    }

    @Override
    protected void onCloseConnection(Host peer, int connection) {
        logger.debug("CloseConnection " + peer);
        Pair<Connection<T>, Queue<T>> remove = pendingOut.remove(peer);
        if (remove != null) remove.getKey().disconnect();

        Connection<T> established = establishedOut.get(peer);
        if (established != null) established.disconnect();
    }

    @Override
    protected void onOutboundConnectionDown(Connection<T> conn, Throwable cause) {
        logger.debug("OutboundConnectionDown " + conn.getPeer() + (cause != null ? (" " + cause) : ""));
        Connection<T> remove = establishedOut.remove(conn.getPeer());
        if (remove == null) throw new RuntimeException("Connection down with no context available");

        listener.deliverEvent(new OutConnectionDown(conn.getPeer(), cause));
    }

    @Override
    protected void onOutboundConnectionFailed(Connection<T> conn, Throwable cause) {
        logger.debug("OutboundConnectionFailed " + conn.getPeer() + (cause != null ? (" " + cause) : ""));
        if (establishedOut.containsKey(conn.getPeer()))
            throw new RuntimeException("Connection exists in conn failed");

        Pair<Connection<T>, Queue<T>> remove = pendingOut.remove(conn.getPeer());
        if (remove == null) throw new RuntimeException("Connection failed with no pending");

        listener.deliverEvent(new OutConnectionFailed<>(conn.getPeer(), remove.getRight(), cause));
    }

    @Override
    protected void onInboundConnectionUp(Connection<T> con) {
        Host clientSocket;
        try {
            clientSocket = con.getAttributes().getHost(LISTEN_ADDRESS_ATTRIBUTE);
        } catch (IOException e) {
            logger.error("Inbound connection without valid listen address: " + e.getMessage());
            con.disconnect();
            return;
        }
        logger.debug("InboundConnectionUp " + clientSocket);

        if (establishedIn.putIfAbsent(clientSocket, con) != null)
            throw new RuntimeException("Double incoming connection from: " + con.getPeer());
        listener.deliverEvent(new InConnectionUp(clientSocket));
    }

    @Override
    protected void onInboundConnectionDown(Connection<T> con, Throwable cause) {
        Host host = establishedIn.removeValue(con);
        logger.debug("InboundConnectionDown " + host + (cause != null ? (" " + cause) : ""));
        listener.deliverEvent(new InConnectionDown(host, cause));
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
        Host host;
        if (conn.isInbound())
            host = establishedIn.getKey(conn);
        else
            host = conn.getPeer();
        logger.debug("DeliverMessage " + msg + " " + host + " " + (conn.isInbound() ? "IN" : "OUT"));
        listener.deliverMessage(msg, host);
    }

    @Override
    public boolean validateAttributes(Attributes attr) {
        Short channel = attr.getShort(CHANNEL_MAGIC_ATTRIBUTE);
        return channel != null && channel == TCP_MAGIC_NUMBER;
    }
}
