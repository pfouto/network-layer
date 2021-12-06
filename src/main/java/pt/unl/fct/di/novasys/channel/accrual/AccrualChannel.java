package pt.unl.fct.di.novasys.channel.accrual;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.channel.ChannelListener;
import pt.unl.fct.di.novasys.channel.accrual.events.PhiEvent;
import pt.unl.fct.di.novasys.channel.accrual.messaging.*;
import pt.unl.fct.di.novasys.channel.base.SingleThreadedBiChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.AttributeValidator;
import pt.unl.fct.di.novasys.network.Connection;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.NetworkManager;
import pt.unl.fct.di.novasys.network.data.Attributes;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Based on the akka implementation of the Phi Accrual Failure Detector:
 * https://github.com/akka/akka/blob/main/akka-remote/src/main/scala/akka/remote/PhiAccrualFailureDetector.scala
 */
public class AccrualChannel<T> extends SingleThreadedBiChannel<T, AccrualMessage<T>> implements AttributeValidator {

    public static final String NAME = "AccrualChannel";
    public static final String ADDRESS_KEY = "address";
    public static final String PORT_KEY = "port";
    public static final String WORKER_GROUP_KEY = "worker_group";
    public static final String TRIGGER_SENT_KEY = "trigger_sent";
    public static final String CONNECT_TIMEOUT_KEY = "connect_timeout";
    public static final String LISTEN_ADDRESS_ATTRIBUTE = "listen_address";
    public static final String DEFAULT_PORT = "8551";
    public static final String DEFAULT_CONNECT_TIMEOUT = "1000";
    public static final int CONNECTION_OUT = 0;
    public static final int CONNECTION_IN = 1;

    //Phi Accrual parameters
    public static final String WINDOW_SIZE_KEY = "window_size";
    public static final String HB_INTERVAL_KEY = "hb_interval";
    public static final String HB_INTERVAL_ATTRIBUTE = HB_INTERVAL_KEY;
    public static final String MIN_STD_DEVIATION_KEY = "std_deviation";
    public static final String ACCEPTABLE_HB_PAUSE_KEY = "acceptable_hb_pause";
    public static final String THRESHOLD_KEY = "threshold";
    public static final String PREDICT_INTERVAL_KEY = "predict_interval";

    public static final String DEFAULT_WINDOW_SIZE = "1000";
    public static final String DEFAULT_HB_INTERVAL = "1000";
    public static final String DEFAULT_MIN_STD_DEVIATION = "200";
    public static final String DEFAULT_ACCEPTABLE_HB_PAUSE = "1000";
    public static final String DEFAULT_THRESHOLD = "-1"; //Always report status
    public static final String DEFAULT_PREDICT_INTERVAL = "100";


    private static final short ACCRUAL_MAGIC_NUMBER = 0x4666;
    private static final Logger logger = LogManager.getLogger(AccrualChannel.class);

    //Host represents the client server socket, not the client tcp connection address!
    //client connection address is in connection.getPeer
    private final Map<Host, LinkedList<InConnectionState<AccrualMessage<T>>>> inConnections;
    private final Map<Host, OutConnectionState<AccrualMessage<T>>> outConnections;
    private final boolean triggerSent;
    private final NetworkManager<AccrualMessage<T>> network;
    private final ChannelListener<T> listener;
    private final Attributes attributes;

    //Phi accrual
    private final int windowSize;
    private final int hbInterval;
    private final int minStdDeviation;
    private final int acceptableHbPause;
    private final double threshold;
    private final int predictInterval;
    private final Map<Host, PhiAccrual> monitors;

    public AccrualChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties) throws IOException {
        super(NAME);
        this.listener = list;

        InetAddress addr;
        if (properties.containsKey(ADDRESS_KEY))
            addr = Inet4Address.getByName(properties.getProperty(ADDRESS_KEY));
        else
            throw new IllegalArgumentException(NAME + " requires binding address");

        int port = Integer.parseInt(properties.getProperty(PORT_KEY, DEFAULT_PORT));
        int connTimeout = Integer.parseInt(properties.getProperty(CONNECT_TIMEOUT_KEY, DEFAULT_CONNECT_TIMEOUT));
        this.triggerSent = Boolean.parseBoolean(properties.getProperty(TRIGGER_SENT_KEY, "false"));
        this.windowSize = Integer.parseInt(properties.getProperty(WINDOW_SIZE_KEY, DEFAULT_WINDOW_SIZE));
        this.hbInterval = Integer.parseInt(properties.getProperty(HB_INTERVAL_KEY, DEFAULT_HB_INTERVAL));
        this.minStdDeviation = Integer.parseInt(properties.getProperty(MIN_STD_DEVIATION_KEY, DEFAULT_MIN_STD_DEVIATION));
        this.acceptableHbPause = Integer.parseInt(properties.getProperty(ACCEPTABLE_HB_PAUSE_KEY, DEFAULT_ACCEPTABLE_HB_PAUSE));
        this.threshold = Double.parseDouble(properties.getProperty(THRESHOLD_KEY, DEFAULT_THRESHOLD));
        this.predictInterval = Integer.parseInt(properties.getProperty(PREDICT_INTERVAL_KEY, DEFAULT_PREDICT_INTERVAL));
        Host listenAddress = new Host(addr, port);

        EventLoopGroup eventExecutors = properties.containsKey(WORKER_GROUP_KEY) ?
                (EventLoopGroup) properties.get(WORKER_GROUP_KEY) :
                NetworkManager.createNewWorkerGroup();

        AccrualMessageSerializer<T> accSerializer = new AccrualMessageSerializer<>(serializer);
        network = new NetworkManager<>(accSerializer, this, 0, 0, connTimeout, eventExecutors);
        network.createServerSocket(this, listenAddress, this, eventExecutors);

        attributes = new Attributes();
        attributes.putShort(CHANNEL_MAGIC_ATTRIBUTE, ACCRUAL_MAGIC_NUMBER);
        attributes.putHost(LISTEN_ADDRESS_ATTRIBUTE, listenAddress);
        attributes.putInt(HB_INTERVAL_ATTRIBUTE, hbInterval);

        inConnections = new HashMap<>();
        outConnections = new HashMap<>();
        monitors = new HashMap<>();

        loop.scheduleAtFixedRate(this::predictPhi, predictInterval, predictInterval, TimeUnit.MILLISECONDS);
    }

    private void predictPhi() {
        Map<Host, Map<String, Double>> values = new HashMap<>();
        long timestamp = System.currentTimeMillis();
        monitors.forEach((k, v) -> {
            Map<String, Double> res = v.phi(timestamp);
            double val = res.get("phi");
            if (threshold <= 0 || val >= threshold)
                values.put(k, res);
        });
        if (!values.isEmpty())
            listener.deliverEvent(new PhiEvent(values));
    }

    @Override
    protected void onOpenConnection(Host peer) {
        OutConnectionState<AccrualMessage<T>> conState = outConnections.get(peer);
        if (conState == null) {
            logger.debug("onOpenConnection creating connection to: " + peer);
            outConnections.put(peer, new OutConnectionState<>(network.createConnection(peer, attributes, this)));
        } else if (conState.getState() == OutConnectionState.State.DISCONNECTING) {
            logger.debug("onOpenConnection reopening after close to: " + peer);
            conState.setState(OutConnectionState.State.DISCONNECTING_RECONNECT);
        } else
            logger.debug("onOpenConnection ignored: " + peer);
    }

    @Override
    protected void onSendMessage(T msg, Host peer, int connection) {
        logger.debug("SendMessage " + msg + " " + peer + " " + (connection == CONNECTION_IN ? "IN" : "OUT"));
        if (connection <= CONNECTION_OUT) {
            OutConnectionState<AccrualMessage<T>> conState = outConnections.get(peer);
            if (conState != null) {
                if (conState.getState() == OutConnectionState.State.CONNECTING ||
                        conState.getState() == OutConnectionState.State.DISCONNECTING_RECONNECT) {
                    conState.getQueue().add(new AccrualAppMessage<>(msg));
                } else if (conState.getState() == OutConnectionState.State.CONNECTED) {
                    sendWithListener(new AccrualAppMessage<>(msg), peer, conState.getConnection());
                } else if (conState.getState() == OutConnectionState.State.DISCONNECTING) {
                    conState.getQueue().add(new AccrualAppMessage<>(msg));
                    conState.setState(OutConnectionState.State.DISCONNECTING_RECONNECT);
                }
            } else
                listener.messageFailed(msg, peer, new IllegalArgumentException("No outgoing connection"));

        } else if (connection == CONNECTION_IN) {
            LinkedList<InConnectionState<AccrualMessage<T>>> inConnList = inConnections.get(peer);
            if (inConnList != null)
                sendWithListener(new AccrualAppMessage<>(msg), peer, inConnList.getLast().getConnection());
            else
                listener.messageFailed(msg, peer, new IllegalArgumentException("No incoming connection"));
        } else {
            listener.messageFailed(msg, peer, new IllegalArgumentException("Invalid connection: " + connection));
            logger.error("Invalid sendMessage mode " + connection);
        }
    }

    private void sendWithListener(AccrualAppMessage<T> msg, Host peer, Connection<AccrualMessage<T>> established) {
        Promise<Void> promise = loop.newPromise();
        promise.addListener(future -> {
            if (future.isSuccess() && triggerSent) listener.messageSent(msg.getPayload(), peer);
            else if (!future.isSuccess()) listener.messageFailed(msg.getPayload(), peer, future.cause());
        });
        established.sendMessage(msg, promise);
    }

    @Override
    protected void onOutboundConnectionUp(Connection<AccrualMessage<T>> conn) {
        logger.debug("OutboundConnectionUp " + conn.getPeer());
        OutConnectionState<AccrualMessage<T>> conState = outConnections.get(conn.getPeer());
        //conState.getConnection().getLoop();
        if (conState == null) {
            throw new AssertionError("ConnectionUp with no conState: " + conn);
        } else if (conState.getState() == OutConnectionState.State.CONNECTED) {
            throw new AssertionError("ConnectionUp in CONNECTED state: " + conn);
        } else if (conState.getState() == OutConnectionState.State.CONNECTING) {
            conState.setState(OutConnectionState.State.CONNECTED);
            monitors.put(conn.getPeer(),
                    new PhiAccrual(windowSize, threshold, minStdDeviation, acceptableHbPause, hbInterval*4));
            conState.getQueue().forEach(m -> sendWithListener((AccrualAppMessage<T>) m, conn.getPeer(), conn));
            conState.getQueue().clear();
            listener.deliverEvent(new OutConnectionUp(conn.getPeer()));
        }
    }

    @Override
    protected void onCloseConnection(Host peer, int connection) {
        logger.debug("CloseConnection " + peer);
        OutConnectionState<AccrualMessage<T>> conState = outConnections.get(peer);
        if (conState != null) {
            if (conState.getState() == OutConnectionState.State.CONNECTED
                    || conState.getState() == OutConnectionState.State.CONNECTING
                    || conState.getState() == OutConnectionState.State.DISCONNECTING_RECONNECT) {
                conState.setState(OutConnectionState.State.DISCONNECTING);
                conState.getQueue().clear();
                conState.getConnection().disconnect();
            }
        }
    }

    @Override
    protected void onOutboundConnectionDown(Connection<AccrualMessage<T>> conn, Throwable cause) {

        logger.debug("OutboundConnectionDown " + conn.getPeer() + (cause != null ? (" " + cause) : ""));
        OutConnectionState<AccrualMessage<T>> conState = outConnections.remove(conn.getPeer());
        monitors.remove(conn.getPeer());
        if (conState == null) {
            throw new AssertionError("ConnectionDown with no conState: " + conn);
        } else {
            if (conState.getState() == OutConnectionState.State.CONNECTING) {
                throw new AssertionError("ConnectionDown in CONNECTING state: " + conn);
            } else if (conState.getState() == OutConnectionState.State.CONNECTED) {
                listener.deliverEvent(new OutConnectionDown(conn.getPeer(), cause));
            } else if (conState.getState() == OutConnectionState.State.DISCONNECTING_RECONNECT) {
                outConnections.put(conn.getPeer(), new OutConnectionState<>(
                        network.createConnection(conn.getPeer(), attributes, this), conState.getQueue()));
            }
        }
    }

    @Override
    protected void onOutboundConnectionFailed(Connection<AccrualMessage<T>> conn, Throwable cause) {
        logger.debug("OutboundConnectionFailed " + conn.getPeer() + (cause != null ? (" " + cause) : ""));

        OutConnectionState<AccrualMessage<T>> conState = outConnections.remove(conn.getPeer());
        if (conState == null) {
            throw new AssertionError("ConnectionFailed with no conState: " + conn);
        } else {
            if (conState.getState() == OutConnectionState.State.CONNECTING)
                listener.deliverEvent(new OutConnectionFailed<>(conn.getPeer(), conState.getQueue(), cause));
            else if (conState.getState() == OutConnectionState.State.DISCONNECTING_RECONNECT)
                outConnections.put(conn.getPeer(), new OutConnectionState<>(
                        network.createConnection(conn.getPeer(), attributes, this), conState.getQueue()));
            else if (conState.getState() == OutConnectionState.State.CONNECTED)
                throw new AssertionError("ConnectionFailed in state: " + conState.getState() + " - " + conn);
        }
    }

    //Called by netty connection thread (not this channel's thread!)
    private void sendHeartbeat(InConnectionState<AccrualMessage<T>> conState) {
        conState.getConnection().sendMessage(new AccrualHbMessage<>(conState.getAndIncCounter()));
    }

    @Override
    protected void onInboundConnectionUp(Connection<AccrualMessage<T>> con) {

        Host clientSocket;
        try {
            clientSocket = con.getPeerAttributes().getHost(LISTEN_ADDRESS_ATTRIBUTE);
        } catch (IOException e) {
            logger.error("Inbound connection without valid listen address in connectionUp: " + e.getMessage());
            con.disconnect();
            return;
        }

        LinkedList<InConnectionState<AccrualMessage<T>>> inConnList =
                inConnections.computeIfAbsent(clientSocket, k -> new LinkedList<>());
        InConnectionState<AccrualMessage<T>> inConnState = new InConnectionState<>(con);
        inConnList.add(inConnState);

        int remoteHbInterval = con.getPeerAttributes().getInt(HB_INTERVAL_ATTRIBUTE);
        ScheduledFuture<?> scheduledFuture = con.getLoop().scheduleAtFixedRate(
                () -> sendHeartbeat(inConnState), remoteHbInterval, remoteHbInterval, TimeUnit.MILLISECONDS);
        inConnState.setPeriodicHbTask(scheduledFuture);

        if (inConnList.size() == 1) {
            logger.debug("InboundConnectionUp " + clientSocket);
            listener.deliverEvent(new InConnectionUp(clientSocket));
        } else {
            logger.debug("Multiple InboundConnectionUp " + inConnList.size() + clientSocket);
        }
    }

    @Override
    protected void onInboundConnectionDown(Connection<AccrualMessage<T>> con, Throwable cause) {
        Host clientSocket;
        try {
            clientSocket = con.getPeerAttributes().getHost(LISTEN_ADDRESS_ATTRIBUTE);
        } catch (IOException e) {
            logger.error("Inbound connection without valid listen address in connectionDown: " + e.getMessage());
            con.disconnect();
            return;
        }

        LinkedList<InConnectionState<AccrualMessage<T>>> inConnList = inConnections.get(clientSocket);
        if (inConnList == null || inConnList.isEmpty())
            throw new AssertionError("No connections in InboundConnectionDown " + clientSocket);

        Optional<InConnectionState<AccrualMessage<T>>> first =
                inConnList.stream().filter(conState -> conState.getConnection() == con).findFirst();
        if (!first.isPresent())
            throw new AssertionError("No connection in InboundConnectionDown " + clientSocket);
        inConnList.remove(first.get());

        first.get().getPeriodicHbTask().cancel(true);

        if (inConnList.isEmpty()) {
            logger.debug("InboundConnectionDown " + clientSocket + (cause != null ? (" " + cause) : ""));
            listener.deliverEvent(new InConnectionDown(clientSocket, cause));
            inConnections.remove(clientSocket);
        } else
            logger.debug("Extra InboundConnectionDown " + inConnList.size() + clientSocket);
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

    private void handleHbMessage(AccrualHbMessage<T> msg, Connection<AccrualMessage<T>> conn) {
        monitors.get(conn.getPeer()).receivedHb(msg.getCounter());
    }

    private void handleAppMessage(AccrualAppMessage<T> msg, Connection<AccrualMessage<T>> conn) {
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
        listener.deliverMessage(msg.getPayload(), host);
    }

    @Override
    public void onDeliverMessage(AccrualMessage<T> msg, Connection<AccrualMessage<T>> conn) {

        switch (msg.getType()) {
            case APP_MSG:
                handleAppMessage((AccrualAppMessage<T>) msg, conn);
                break;
            case HB:
                handleHbMessage((AccrualHbMessage<T>) msg, conn);
                break;
        }
    }

    @Override
    public boolean validateAttributes(Attributes attr) {
        Short channel = attr.getShort(CHANNEL_MAGIC_ATTRIBUTE);
        return channel != null && channel == ACCRUAL_MAGIC_NUMBER;
    }
}
