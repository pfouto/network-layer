package pt.unl.fct.di.novasys.channel.tcp;

import pt.unl.fct.di.novasys.channel.ChannelListener;
import pt.unl.fct.di.novasys.channel.IChannel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Promise;
import pt.unl.fct.di.novasys.network.AttributeValidator;
import pt.unl.fct.di.novasys.network.Connection;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.NetworkManager;
import pt.unl.fct.di.novasys.network.data.Attributes;
import pt.unl.fct.di.novasys.network.data.Host;
import pt.unl.fct.di.novasys.network.listeners.InConnListener;
import pt.unl.fct.di.novasys.network.listeners.MessageListener;
import pt.unl.fct.di.novasys.network.listeners.OutConnListener;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.channel.tcp.events.*;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/***
 * Multithreaded channel with explicit openConnection.
 */
public class MultithreadedTCPChannel<T> implements IChannel<T>, MessageListener<T>, InConnListener<T>,
        OutConnListener<T>, AttributeValidator {

    private static final Logger logger = LogManager.getLogger(MultithreadedTCPChannel.class);
    private static final short TCP_MAGIC_NUMBER = 0x4505;

    public final static String NAME = "MultithreadedTCPChannel";
    public final static String ADDRESS_KEY = "address";
    public final static String PORT_KEY = "port";
    public final static String WORKER_GROUP_KEY = "workerGroup";

    public static final String LISTEN_ADDRESS_ATTRIBUTE = "listen_address";

    public final static int DEFAULT_PORT = 8573;
    public final static int CONNECTION_OUT = 0;
    public final static int CONNECTION_IN = 1;

    private final NetworkManager<T> network;
    private final ChannelListener<T> listener;

    private Attributes attributes;

    private Map<Host, Pair<Connection<T>, Queue<T>>> pendingOut;
    private Map<Host, Connection<T>> establishedOut;

    //Host represents the client server socket, not the client tcp connection address!
    //client connection address is in connection.getPeer
    private final BidiMap<Host, Connection<T>> establishedIn;

    public MultithreadedTCPChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties)
            throws IOException {

        this.listener = list;

        InetAddress addr;
        if (properties.containsKey(ADDRESS_KEY))
            addr = Inet4Address.getByName(properties.getProperty(ADDRESS_KEY));
        else
            throw new IllegalArgumentException(NAME + " requires binding address");

        int port = properties.containsKey(PORT_KEY) ? (int)(properties.get(PORT_KEY)) : DEFAULT_PORT;

        Host listenAddress = new Host(addr, port);

        EventLoopGroup eventExecutors = properties.containsKey(WORKER_GROUP_KEY) ?
                (EventLoopGroup) properties.get(WORKER_GROUP_KEY) :
                NetworkManager.createNewWorkerGroup(0);
        network = new NetworkManager<>(serializer, this, 1000, 3000, 1000, eventExecutors);
        network.createServerSocket(this, new Host(addr, port), this, eventExecutors);

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
        pendingOut.computeIfAbsent(peer, k ->
                Pair.of(network.createConnection(peer, attributes, this), new LinkedList<>()));
    }

    @Override
    public void sendMessage(T msg, Host peer, int connection) {
        logger.debug("SendMessage " + msg + " " + peer + " " + (connection == CONNECTION_IN ? "IN" : "OUT"));
        if (connection <= CONNECTION_OUT) {
            Connection<T> established = establishedOut.get(peer);
            Pair<Connection<T>, Queue<T>> pending;
            if (established != null)
                sendWithListener(msg, peer, established);
            else if ((pending = pendingOut.get(peer)) != null)
                pending.getValue().add(msg);
            else
                listener.messageFailed(msg, peer, new IllegalArgumentException("No outgoing connection to peer."));
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
        Pair<Connection<T>, Queue<T>> remove = pendingOut.remove(peer);
        if (remove != null) remove.getKey().disconnect();

        Connection<T> established = establishedOut.get(peer);
        if (established != null) established.disconnect();
    }

    // ********************************* Called in loop *******************************************************
    @Override
    public void outboundConnectionUp(Connection<T> conn) {
        assert conn.getLoop().inEventLoop();
        logger.debug("OutboundConnectionUp " + conn.getPeer());
        Pair<Connection<T>, Queue<T>> remove = pendingOut.remove(conn.getPeer());
        if (remove != null) {
            if (remove.getKey() != conn) throw new RuntimeException("Reference mismatch");
            Connection<T> put = establishedOut.put(conn.getPeer(), conn);
            if (put != null) throw new RuntimeException("Connection already exists in connection up");
            listener.deliverEvent(new OutConnectionUp(conn.getPeer()));
            remove.getValue().forEach(t -> sendWithListener(t, conn.getPeer(), conn));
        } else {
            logger.warn("ConnectionUp with no pending: " + conn);
        }
    }

    @Override
    public void outboundConnectionDown(Connection<T> conn, Throwable cause) {
        assert conn.getLoop().inEventLoop();
        logger.debug("OutboundConnectionDown " + conn.getPeer() + (cause != null ? (" " + cause) : ""));
        Connection<T> remove = establishedOut.remove(conn.getPeer());
        if (remove != null) {
            listener.deliverEvent(new OutConnectionDown(conn.getPeer(), cause));
        } else {
            logger.warn("ConnectionDown with no context available: " + conn);
        }
    }

    @Override
    public void outboundConnectionFailed(Connection<T> conn, Throwable cause) {
        assert conn.getLoop().inEventLoop();
        logger.debug("OutboundConnectionFailed " + conn.getPeer() + (cause != null ? (" " + cause) : ""));
        if (establishedOut.containsKey(conn.getPeer()))
            throw new RuntimeException("Connection exists in conn failed");

        Pair<Connection<T>, Queue<T>> remove = pendingOut.remove(conn.getPeer());
        if (remove != null) {
            listener.deliverEvent(new OutConnectionFailed<>(conn.getPeer(), remove.getRight(), cause));
        } else {
            logger.warn("ConnectionFailed with no pending: " + conn);
        }
    }

    @Override
    public void inboundConnectionUp(Connection<T> con) {
        assert con.getLoop().inEventLoop();
        Host clientSocket;
        try {
            clientSocket = con.getPeerAttributes().getHost(LISTEN_ADDRESS_ATTRIBUTE);
        } catch (IOException e) {
            logger.error("Error parsing LISTEN_ADDRESS_ATTRIBUTE of inbound connection: " + e.getMessage());
            con.disconnect();
            return;
        }

        if (clientSocket == null) {
            logger.error("Inbound connection without LISTEN_ADDRESS: " + con.getPeer() + " " + con.getPeerAttributes());
            return;
        }

        logger.debug("InboundConnectionUp " + clientSocket);

        Connection<T> old;
        synchronized (establishedIn) {
            old = establishedIn.putIfAbsent(clientSocket, con);
        }
        if (old != null)
            throw new RuntimeException("Double incoming connection from: " + clientSocket + " (" + con.getPeer() + ")");

        listener.deliverEvent(new InConnectionUp(clientSocket));
    }

    @Override
    public void inboundConnectionDown(Connection<T> conn, Throwable cause) {
        assert conn.getLoop().inEventLoop();
        Host host;
        synchronized (establishedIn) {
            host = establishedIn.removeValue(conn);
        }
        logger.debug("InboundConnectionDown " + host + (cause != null ? (" " + cause) : ""));
        listener.deliverEvent(new InConnectionDown(host, cause));
    }

    @Override
    public void deliverMessage(T msg, Connection<T> conn) {
        assert conn.getLoop().inEventLoop();
        Host host;
        if (conn.isInbound()) {
            host = establishedIn.getKey(conn);
            if (host == null)
                throw new AssertionError("Null host");
        } else
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
