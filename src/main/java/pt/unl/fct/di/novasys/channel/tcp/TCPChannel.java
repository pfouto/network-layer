package pt.unl.fct.di.novasys.channel.tcp;

import pt.unl.fct.di.novasys.channel.ChannelListener;
import pt.unl.fct.di.novasys.channel.base.SingleThreadedBiChannel;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Promise;
import pt.unl.fct.di.novasys.network.AttributeValidator;
import pt.unl.fct.di.novasys.network.Connection;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.NetworkManager;
import pt.unl.fct.di.novasys.network.data.Attributes;
import pt.unl.fct.di.novasys.network.data.Host;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.channel.tcp.events.*;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 *  This channel is used for peer-to-peer applications, with every peer being able to both receive and establish
 *  connections.
 *
 *  <h3>Channel Properties</h3>
 *
 *  To use this channel, the following properties need to be provided:
 *  <ul>
 *  <li>Required:
 *      <ul>
 *      <li>{@link TCPChannel#ADDRESS_KEY}: the address to bind the listen socket to</li>
 *      <li>{@link TCPChannel#PORT_KEY}: the port to bind the listen socket to</li>
 *      </ul>
 *  </li>
 *  <li>Optional:
 *      <ul>
 *      <li>{@link TCPChannel#WORKER_GROUP_KEY}: a custom thread pool can be passed in this argument. This thread pool will be used to handle
 *                          all connection events (receiving new connection, (de)serializing messages, etc).
 *                          By default, a thread pool with 2x the number of available cores is created.</li>
 *      <li>{@link TCPChannel#TRIGGER_SENT_KEY}: If set to true, a {@code messageSent} event is triggered upon sending a message (defaults to false)</li>
 *      <li>{@link TCPChannel#METRICS_INTERVAL_KEY}: If set to a value greater than 0, a special {@link ChannelMetrics} event is generated periodically
 *                              (in intervals corresponding to the value given). These events contain information about
 *                              all connections managed by this channel (defaults to -1, which never triggers these events).</li>
 *      <li>{@link TCPChannel#HEARTBEAT_INTERVAL_KEY}: The interval between sending each heartbeat in milliseconds. Defaults to 1000 (1 sec)</li>
 *      <li>{@link TCPChannel#HEARTBEAT_TOLERANCE_KEY}: The time without receiving heartbeats after which a connection is closed. Used for
 *                                 fault detection, defaults to 3000 (3 seconds).</li>
 *      <li>{@link TCPChannel#CONNECT_TIMEOUT_KEY}: The timeout for the TCP connection establishment. Defaults to 1000 (1 sec).</li>
 *      </ul>
 *  </li>
 *  </ul>
 *
 *  <h3>Usage</h3>
 *
 *  This channel requires explicit connection opening, meaning that if you send a message without first calling
 *  {@code openConnection(Host peer)}, the message will not be sent (and a {@code messageFailed} event will be triggered). You can, however,
 *  start sending messages before the connection is established and the {@link OutConnectionUp} event is triggered. These
 *  messages will be queued and sent as soon as the connection is established. If the connection fails to establish,
 *  the {@link OutConnectionFailed} event will return these messages.
 * <p>
 *  <h3>Regular Flow</h3>
 *
 *  The recommended way to use the channel is as follows (assuming no connection problems):
 * <pre>
 *      1. Call {@code openConnection(Host peer)} to create a connection to a peer.
 *      1.1 Optionally, you can start queueing up messages by using {@code sendMessage(ProtoMessage msg, Host destination, ...)}
 *      2. Wait until the {@link OutConnectionUp} event is triggered.
 *      3. Send messages using {@code sendMessage(...)}.
 *      4. Call {@code closeConnection(Host peer)} and stop sending messages (as they will all result in a message failed event).
 * </pre>
 *  By calling {@code closeConnection(Host peer)} the connection is closed only after sending all messages from previous {@code sendMessage(...)}
 *  calls.
 *
 *  <h3>Handling faults</h3>
 *  If the connection is dropped anywhere after step 2, a {@link OutConnectionDown} event is triggered.
 *  Stop sending messages (as they will all result in {@code messageFailed} events) and return to step 1.
 *  In this case, there are no guarantees that messages sent in previous {@code sendMessage(...)}
 *  calls reached their destination.
 *
 *  <p>
 *
 *  If the connection attempt fails, i.e. a {@link OutConnectionFailed} event is received in step 2, the same applies. Stop
 *  sending messages and return to step 1. In this case, no messages were sent and the event returns all messages that
 *  were queued in step 1.1.
 *
 * <p>
 *  <h3>Incoming connections</h3>
 *
 *  Additionally, the events {@link InConnectionUp} event and {@link InConnectionDown} event are triggered whenever a peer establishes a connection
 *  to this process. Messages can also be sent though these incoming connections (as opposed to the outgoing connections
 *  established by {@code openConnection(Host peer)}) by passing value {@link TCPChannel#CONNECTION_IN} in the parameter
 *  {@code connection} of {@code sendMessage(..., int connection)}.
 *  This does not require a previous {@code openConnection(Host peer)} (as we are using an incoming connection).
 *
 */
public class TCPChannel<T> extends SingleThreadedBiChannel<T, T> implements AttributeValidator {

    private static final Logger logger = LogManager.getLogger(TCPChannel.class);
    private static final short TCP_MAGIC_NUMBER = 0x4505;

    public final static String NAME = "TCPChannel";

    public final static String ADDRESS_KEY = "address";
    public final static String PORT_KEY = "port";
    public final static String WORKER_GROUP_KEY = "worker_group";
    public final static String TRIGGER_SENT_KEY = "trigger_sent";
    public final static String METRICS_INTERVAL_KEY = "metrics_interval";
    public final static String HEARTBEAT_INTERVAL_KEY = "heartbeat_interval";
    public final static String HEARTBEAT_TOLERANCE_KEY = "heartbeat_tolerance";
    public final static String CONNECT_TIMEOUT_KEY = "connect_timeout";

    public static final String LISTEN_ADDRESS_ATTRIBUTE = "listen_address";

    public final static String DEFAULT_PORT = "8573";
    public final static String DEFAULT_HB_INTERVAL = "0";
    public final static String DEFAULT_HB_TOLERANCE = "0";
    public final static String DEFAULT_CONNECT_TIMEOUT = "1000";
    public final static String DEFAULT_METRICS_INTERVAL = "-1";

    public final static int CONNECTION_OUT = 0;
    public final static int CONNECTION_IN = 1;

    private final NetworkManager<T> network;
    private final ChannelListener<T> listener;

    private final Attributes attributes;

    //Host represents the client server socket, not the client tcp connection address!
    //client connection address is in connection.getPeer
    private final Map<Host, LinkedList<Connection<T>>> inConnections;
    private final Map<Host, ConnectionState<T>> outConnections;

    private List<Pair<Host, Connection<T>>> oldIn;
    private List<Pair<Host, ConnectionState<T>>> oldOUt;

    private final boolean triggerSent;
    private final boolean metrics;

    public TCPChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties) throws IOException {
        super("TCPChannel");
        this.listener = list;

        InetAddress addr;
        if (properties.containsKey(ADDRESS_KEY))
            addr = Inet4Address.getByName(properties.getProperty(ADDRESS_KEY));
        else
            throw new IllegalArgumentException(NAME + " requires binding address");

        int port = Integer.parseInt(properties.getProperty(PORT_KEY, DEFAULT_PORT));
        int hbInterval = Integer.parseInt(properties.getProperty(HEARTBEAT_INTERVAL_KEY, DEFAULT_HB_INTERVAL));
        int hbTolerance = Integer.parseInt(properties.getProperty(HEARTBEAT_TOLERANCE_KEY, DEFAULT_HB_TOLERANCE));
        int connTimeout = Integer.parseInt(properties.getProperty(CONNECT_TIMEOUT_KEY, DEFAULT_CONNECT_TIMEOUT));
        int metricsInterval = Integer.parseInt(properties.getProperty(METRICS_INTERVAL_KEY, DEFAULT_METRICS_INTERVAL));
        this.triggerSent = Boolean.parseBoolean(properties.getProperty(TRIGGER_SENT_KEY, "false"));
        this.metrics = metricsInterval > 0;

        Host listenAddress = new Host(addr, port);

        EventLoopGroup eventExecutors = properties.containsKey(WORKER_GROUP_KEY) ?
                (EventLoopGroup) properties.get(WORKER_GROUP_KEY) :
                NetworkManager.createNewWorkerGroup();

        network = new NetworkManager<>(serializer, this, hbInterval, hbTolerance, connTimeout, eventExecutors);
        network.createServerSocket(this, listenAddress, this, eventExecutors);

        attributes = new Attributes();
        attributes.putShort(CHANNEL_MAGIC_ATTRIBUTE, TCP_MAGIC_NUMBER);
        attributes.putHost(LISTEN_ADDRESS_ATTRIBUTE, listenAddress);

        inConnections = new HashMap<>();
        outConnections = new HashMap<>();

        if (metrics) {
            oldIn = new LinkedList<>();
            oldOUt = new LinkedList<>();
            loop.scheduleAtFixedRate(this::triggerMetricsEvent, metricsInterval, metricsInterval, TimeUnit.MILLISECONDS);
        }
    }

    void triggerMetricsEvent() {
        listener.deliverEvent(new ChannelMetrics(oldIn, oldOUt, inConnections, outConnections));
    }

    @Override
    protected void onOpenConnection(Host peer) {
        ConnectionState<T> conState = outConnections.get(peer);
        if (conState == null) {
            logger.debug("onOpenConnection creating connection to: " + peer);
            outConnections.put(peer, new ConnectionState<>(network.createConnection(peer, attributes, this)));
        } else if (conState.getState() == ConnectionState.State.DISCONNECTING) {
            logger.debug("onOpenConnection reopening after close to: " + peer);
            conState.setState(ConnectionState.State.DISCONNECTING_RECONNECT);
        } else
            logger.debug("onOpenConnection ignored: " + peer);
    }

    @Override
    protected void onSendMessage(T msg, Host peer, int connection) {
        logger.debug("SendMessage " + msg + " " + peer + " " + (connection == CONNECTION_IN ? "IN" : "OUT"));
        if (connection <= CONNECTION_OUT) {
            ConnectionState<T> conState = outConnections.get(peer);
            if (conState != null) {
                if (conState.getState() == ConnectionState.State.CONNECTING || conState.getState() == ConnectionState.State.DISCONNECTING_RECONNECT) {
                    conState.getQueue().add(msg);
                } else if (conState.getState() == ConnectionState.State.CONNECTED) {
                    sendWithListener(msg, peer, conState.getConnection());
                } else if (conState.getState() == ConnectionState.State.DISCONNECTING) {
                    conState.getQueue().add(msg);
                    conState.setState(ConnectionState.State.DISCONNECTING_RECONNECT);
                }
            } else
                listener.messageFailed(msg, peer, new IllegalArgumentException("No outgoing connection"));

        } else if (connection == CONNECTION_IN) {
            LinkedList<Connection<T>> inConnList = inConnections.get(peer);
            if (inConnList != null)
                sendWithListener(msg, peer, inConnList.getLast());
            else
                listener.messageFailed(msg, peer, new IllegalArgumentException("No incoming connection"));
        } else {
            listener.messageFailed(msg, peer, new IllegalArgumentException("Invalid connection: " + connection));
            logger.error("Invalid sendMessage mode " + connection);
        }
    }

    private void sendWithListener(T msg, Host peer, Connection<T> established) {
        Promise<Void> promise = loop.newPromise();
        promise.addListener(future -> {
            if (future.isSuccess() && triggerSent) listener.messageSent(msg, peer);
            else if (!future.isSuccess()) listener.messageFailed(msg, peer, future.cause());
        });
        established.sendMessage(msg, promise);
    }

    @Override
    protected void onOutboundConnectionUp(Connection<T> conn) {
        logger.debug("OutboundConnectionUp " + conn.getPeer());
        ConnectionState<T> conState = outConnections.get(conn.getPeer());
        if (conState == null) {
            throw new AssertionError("ConnectionUp with no conState: " + conn);
        } else if (conState.getState() == ConnectionState.State.CONNECTED) {
            throw new AssertionError("ConnectionUp in CONNECTED state: " + conn);
        } else if (conState.getState() == ConnectionState.State.CONNECTING) {
            conState.setState(ConnectionState.State.CONNECTED);
            conState.getQueue().forEach(m -> sendWithListener(m, conn.getPeer(), conn));
            conState.getQueue().clear();
            listener.deliverEvent(new OutConnectionUp(conn.getPeer()));
        }
    }

    @Override
    protected void onCloseConnection(Host peer, int connection) {
        logger.debug("CloseConnection " + peer);
        ConnectionState<T> conState = outConnections.get(peer);
        if (conState != null) {
            if (conState.getState() == ConnectionState.State.CONNECTED || conState.getState() == ConnectionState.State.CONNECTING
                    || conState.getState() == ConnectionState.State.DISCONNECTING_RECONNECT) {
                conState.setState(ConnectionState.State.DISCONNECTING);
                conState.getQueue().clear();
                conState.getConnection().disconnect();
            }
        }
    }

    @Override
    protected void onOutboundConnectionDown(Connection<T> conn, Throwable cause) {

        logger.debug("OutboundConnectionDown " + conn.getPeer() + (cause != null ? (" " + cause) : ""));
        ConnectionState<T> conState = outConnections.remove(conn.getPeer());
        if (conState == null) {
            throw new AssertionError("ConnectionDown with no conState: " + conn);
        } else {
            if (conState.getState() == ConnectionState.State.CONNECTING) {
                throw new AssertionError("ConnectionDown in CONNECTING state: " + conn);
            } else if (conState.getState() == ConnectionState.State.CONNECTED) {
                listener.deliverEvent(new OutConnectionDown(conn.getPeer(), cause));
            } else if (conState.getState() == ConnectionState.State.DISCONNECTING_RECONNECT) {
                outConnections.put(conn.getPeer(), new ConnectionState<>(
                        network.createConnection(conn.getPeer(), attributes, this), conState.getQueue()));
            }
            if (metrics)
                oldOUt.add(Pair.of(conn.getPeer(), conState));
        }
    }

    @Override
    protected void onOutboundConnectionFailed(Connection<T> conn, Throwable cause) {
        logger.debug("OutboundConnectionFailed " + conn.getPeer() + (cause != null ? (" " + cause) : ""));

        ConnectionState<T> conState = outConnections.remove(conn.getPeer());
        if (conState == null) {
            throw new AssertionError("ConnectionFailed with no conState: " + conn);
        } else {
            if (conState.getState() == ConnectionState.State.CONNECTING)
                listener.deliverEvent(new OutConnectionFailed<>(conn.getPeer(), conState.getQueue(), cause));
            else if (conState.getState() == ConnectionState.State.DISCONNECTING_RECONNECT)
                outConnections.put(conn.getPeer(), new ConnectionState<>(
                        network.createConnection(conn.getPeer(), attributes, this), conState.getQueue()));
            else if (conState.getState() == ConnectionState.State.CONNECTED)
                throw new AssertionError("ConnectionFailed in state: " + conState.getState() + " - " + conn);

            if (metrics)
                oldOUt.add(Pair.of(conn.getPeer(), conState));
        }
    }

    @Override
    protected void onInboundConnectionUp(Connection<T> con) {
        Host clientSocket;
        try {
            clientSocket = con.getPeerAttributes().getHost(LISTEN_ADDRESS_ATTRIBUTE);
        } catch (IOException e) {
            logger.error("Inbound connection without valid listen address in connectionUp: " + e.getMessage());
            con.disconnect();
            return;
        }

        LinkedList<Connection<T>> inConnList = inConnections.computeIfAbsent(clientSocket, k -> new LinkedList<>());
        inConnList.add(con);
        if (inConnList.size() == 1) {
            logger.debug("InboundConnectionUp " + clientSocket);
            listener.deliverEvent(new InConnectionUp(clientSocket));
        } else {
            logger.debug("Multiple InboundConnectionUp " + inConnList.size() + clientSocket);
        }
    }

    @Override
    protected void onInboundConnectionDown(Connection<T> con, Throwable cause) {
        Host clientSocket;
        try {
            clientSocket = con.getPeerAttributes().getHost(LISTEN_ADDRESS_ATTRIBUTE);
        } catch (IOException e) {
            logger.error("Inbound connection without valid listen address in connectionDown: " + e.getMessage());
            con.disconnect();
            return;
        }

        LinkedList<Connection<T>> inConnList = inConnections.get(clientSocket);
        if (inConnList == null || inConnList.isEmpty())
            throw new AssertionError("No connections in InboundConnectionDown " + clientSocket);
        if (!inConnList.remove(con))
            throw new AssertionError("No connection in InboundConnectionDown " + clientSocket);

        if (inConnList.isEmpty()) {
            logger.debug("InboundConnectionDown " + clientSocket + (cause != null ? (" " + cause) : ""));
            listener.deliverEvent(new InConnectionDown(clientSocket, cause));
            inConnections.remove(clientSocket);
        } else
            logger.debug("Extra InboundConnectionDown " + inConnList.size() + clientSocket);

        if (metrics)
            oldIn.add(Pair.of(clientSocket, con));
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
            try {
                host = conn.getPeerAttributes().getHost(LISTEN_ADDRESS_ATTRIBUTE);
            } catch (IOException e) {
                logger.error("Inbound connection without valid listen address in deliver message: " + e.getMessage());
                conn.disconnect();
                return;
            }
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
