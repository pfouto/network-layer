package channel.tcp;

import channel.ChannelListener;
import channel.IChannel;
import channel.base.SingleThreadedBiChannel;
import channel.tcp.events.*;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Promise;
import network.AttributeValidator;
import network.Connection;
import network.ISerializer;
import network.NetworkManager;
import network.data.Attributes;
import network.data.Host;
import network.listeners.InConnListener;
import network.listeners.MessageListener;
import network.listeners.OutConnListener;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/***
 * Multithreaded channel without send buffers.
 * Usage: Call openConnection, and only when a OutConnectionUp event is received it is safe to use sendMessage.
 * Calling sendMessage immediately after openConnection will likely result in a messageFailed event, as the connection
 * is not yet established.
 */
public class MultithreadedTCPChannel<T> implements IChannel<T>, MessageListener<T>, InConnListener<T>,
        OutConnListener<T>, AttributeValidator {

    private static final Logger logger = LogManager.getLogger(MultithreadedTCPChannel.class);
    private static final short TCP_MAGIC_NUMBER = 0x4505;

    public static final String LISTEN_ADDRESS_ATTRIBUTE = "listen_address";

    public final static int DEFAULT_PORT = 85739;
    public final static int CONNECTION_OUT = 0;
    public final static int CONNECTION_IN = 1;

    private final NetworkManager<T> network;
    private final ChannelListener<T> listener;

    private Attributes attributes;

    private Map<Host, Connection<T>> pendingOut;
    private Map<Host, Connection<T>> establishedOut;

    //Host represents the client server socket, not the client tcp connection address!
    //client connection address is in connection.getPeer
    private BidiMap<Host, Connection<T>> establishedIn;

    public MultithreadedTCPChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties)
            throws IOException {

        this.listener = list;

        InetAddress addr = null;
        if (properties.containsKey("address"))
            addr = Inet4Address.getByName(properties.getProperty("address"));

        if (addr == null)
            throw new AssertionError("No address received in TCP Channel properties");

        int port = DEFAULT_PORT;
        if (properties.containsKey("port"))
            port = Integer.parseInt(properties.getProperty("port"));

        EventLoopGroup workerGroup = NetworkManager.createNewWorkerGroup(0);
        Host listenAddress = new Host(addr, port);

        network = new NetworkManager<>(serializer, this, 1000, 3000, 1000, workerGroup);
        network.createServerSocket(this, listenAddress, this, workerGroup);

        attributes = new Attributes();
        attributes.putShort(CHANNEL_MAGIC_ATTRIBUTE, TCP_MAGIC_NUMBER);
        attributes.putHost(LISTEN_ADDRESS_ATTRIBUTE, listenAddress);

        pendingOut = new ConcurrentHashMap<>();
        establishedOut = new ConcurrentHashMap<>();
        establishedIn = new DualHashBidiMap<>();
    }

    @Override
    public void openConnection(Host peer) {
        if (establishedOut.containsKey(peer)) return;
        pendingOut.computeIfAbsent(peer, k -> network.createConnection(peer, attributes, this));
    }

    @Override
    public void sendMessage(T msg, Host peer, int connection) {
        logger.debug("SendMessage " + msg + " " + peer + " " + (connection == CONNECTION_IN ? "IN" : "OUT"));
        if (connection <= CONNECTION_OUT) {
            Connection<T> established = establishedOut.get(peer);
            if (established != null) sendWithListener(msg, peer, established);
            else listener.messageFailed(msg, peer, new IllegalArgumentException("No outgoing connection to peer."));
        } else if (connection == CONNECTION_IN) {
            Connection<T> inConn = establishedIn.get(peer);
            if (inConn != null) sendWithListener(msg, peer, inConn);
            else listener.messageFailed(msg, peer, new IllegalArgumentException("No incoming connection"));
        } else {
            listener.messageFailed(msg, peer, new IllegalArgumentException("Invalid send connection: " + connection));
            logger.error("Invalid sendMessage connection " + connection);
        }
    }

    private void sendWithListener(T msg, Host peer, Connection<T> conn) {
        EventLoop loop = conn.getLoop();
        loop.submit(() -> {
            Promise<Void> promise = loop.newPromise();
            promise.addListener(future -> {
                if (future.isSuccess()) listener.messageSent(msg, peer);
                else listener.messageFailed(msg, peer, future.cause());
            });
            conn.sendMessage(msg, promise);
        });
    }

    @Override
    public void closeConnection(Host peer, int connection) {
        logger.debug("CloseConnection " + peer);
        Connection<T> remove = pendingOut.remove(peer);
        if (remove != null) remove.disconnect();

        Connection<T> established = establishedOut.get(peer);
        if (established != null) established.disconnect();
    }

    // ********************************* Called in loop *******************************************************
    @Override
    public void outboundConnectionUp(Connection<T> conn) {
        assert conn.getLoop().inEventLoop();
        logger.debug("OutboundConnectionUp " + conn.getPeer());
        Connection<T> put = establishedOut.put(conn.getPeer(), conn);
        if (put != null) throw new RuntimeException("Connection already exists in connection up");
        Connection<T> remove = pendingOut.remove(conn.getPeer());
        if (remove == null) throw new RuntimeException("Pending null in connection up");
        if (remove != conn) throw new RuntimeException("Reference mismatch");
        listener.deliverEvent(new OutConnectionUp(conn.getPeer()));
    }

    @Override
    public void outboundConnectionDown(Connection<T> conn, Throwable cause) {
        assert conn.getLoop().inEventLoop();
        logger.debug("OutboundConnectionDown " + conn.getPeer() + (cause != null ? (" " + cause) : ""));
        Connection<T> remove = establishedOut.remove(conn.getPeer());
        if (remove == null) throw new RuntimeException("Connection down with no context available");

        listener.deliverEvent(new OutConnectionDown(conn.getPeer(), cause));
    }

    @Override
    public void outboundConnectionFailed(Connection<T> conn, Throwable cause) {
        assert conn.getLoop().inEventLoop();
        logger.debug("OutboundConnectionFailed " + conn.getPeer() + (cause != null ? (" " + cause) : ""));
        if (establishedOut.containsKey(conn.getPeer()))
            throw new RuntimeException("Connection exists in conn failed");

        Connection<T> remove = pendingOut.remove(conn.getPeer());
        if (remove == null) throw new RuntimeException("Connection failed with no pending");

        listener.deliverEvent(new OutConnectionFailed<>(conn.getPeer(), null, cause));
    }

    @Override
    public void inboundConnectionUp(Connection<T> con) {
        assert con.getLoop().inEventLoop();
        Host clientSocket;
        try {
            clientSocket = con.getAttributes().getHost(LISTEN_ADDRESS_ATTRIBUTE);
        } catch (IOException e) {
            logger.error("Error parsing LISTEN_ADDRESS_ATTRIBUTE of inbound connection: " + e.getMessage());
            con.disconnect();
            return;
        }

        if (clientSocket == null) {
            logger.error("Inbound connection without LISTEN_ADDRESS: " + con.getPeer() + " " + con.getAttributes());
            return;
        }

        logger.debug("InboundConnectionUp " + clientSocket);

        if (establishedIn.putIfAbsent(clientSocket, con) != null)
            throw new RuntimeException("Double incoming connection from: " + clientSocket + " (" + con.getPeer() + ")");
        listener.deliverEvent(new InConnectionUp(clientSocket));
    }

    @Override
    public void inboundConnectionDown(Connection<T> conn, Throwable cause) {
        assert conn.getLoop().inEventLoop();
        Host host = establishedIn.removeValue(conn);
        logger.debug("InboundConnectionDown " + host + (cause != null ? (" " + cause) : ""));
        listener.deliverEvent(new InConnectionDown(host, cause));
    }

    @Override
    public void deliverMessage(T msg, Connection<T> conn) {
        assert conn.getLoop().inEventLoop();
        Host host;
        if (conn.isInbound())
            host = establishedIn.getKey(conn);
        else
            host = conn.getPeer();
        logger.debug("DeliverMessage " + msg + " " + host + " " + (conn.isInbound() ? "IN" : "OUT"));
        listener.deliverMessage(msg, host);
    }

    @Override
    public void serverSocketBind(boolean success, Throwable cause) {
        if (success)
            logger.debug("Server socket ready");
        else
            logger.error("Server socket bind failed: " + cause);
    }

    @Override
    public void serverSocketClose(boolean success, Throwable cause) {
        logger.debug("Server socket closed. " + (success ? "" : "Cause: " + cause));
    }

    @Override
    public boolean validateAttributes(Attributes attr) {
        Short channel = attr.getShort(CHANNEL_MAGIC_ATTRIBUTE);
        return channel != null && channel == TCP_MAGIC_NUMBER;
    }
}
