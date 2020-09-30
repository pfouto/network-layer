package channel.tcp;

import channel.ChannelListener;
import channel.base.SingleThreadedBiChannel;
import channel.tcp.ConnectionState.State;
import channel.tcp.events.*;
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

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class TCPChannel<T> extends SingleThreadedBiChannel<T, T> implements AttributeValidator {

    private static final Logger logger = LogManager.getLogger(TCPChannel.class);
    private static final short TCP_MAGIC_NUMBER = 0x4505;

    public final static String NAME = "TCPChannel";
    public final static String ADDRESS_KEY = "address";
    public final static String PORT_KEY = "port";
    public final static String WORKER_GROUP_KEY = "worker_group";
    public final static String TRIGGER_SENT_KEY = "trigger_sent";
    public final static String DEBUG_INTERVAL_KEY = "debug_interval";


    public static final String LISTEN_ADDRESS_ATTRIBUTE = "listen_address";

    public final static int DEFAULT_PORT = 85739;
    public final static int CONNECTION_OUT = 0;
    public final static int CONNECTION_IN = 1;

    private final NetworkManager<T> network;
    private final ChannelListener<T> listener;

    private final Attributes attributes;

    //Host represents the client server socket, not the client tcp connection address!
    //client connection address is in connection.getPeer
    private final Map<Host, LinkedList<Connection<T>>> inConnections;
    private final Map<Host, ConnectionState<T>> outConnections;

    private final boolean triggerSent;

    public TCPChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties) throws IOException {
        super("TCPChannel");
        this.listener = list;

        InetAddress addr = null;
        if (properties.containsKey(ADDRESS_KEY))
            addr = Inet4Address.getByName(properties.getProperty(ADDRESS_KEY));
        else
            throw new IllegalArgumentException(NAME + " requires binding address");

        int port = properties.containsKey(PORT_KEY) ? (Integer) (properties.get(PORT_KEY)) : DEFAULT_PORT;

        Host listenAddress = new Host(addr, port);

        EventLoopGroup eventExecutors = properties.containsKey(WORKER_GROUP_KEY) ?
                (EventLoopGroup) properties.get(WORKER_GROUP_KEY) :
                NetworkManager.createNewWorkerGroup(0);

        network = new NetworkManager<>(serializer, this, 1000, 3000, 1000, eventExecutors);

        network.createServerSocket(this, listenAddress, this, eventExecutors);

        attributes = new Attributes();
        attributes.putShort(CHANNEL_MAGIC_ATTRIBUTE, TCP_MAGIC_NUMBER);
        attributes.putHost(LISTEN_ADDRESS_ATTRIBUTE, listenAddress);

        inConnections = new HashMap<>();
        outConnections = new HashMap<>();

        triggerSent = Boolean.parseBoolean(properties.getProperty(TRIGGER_SENT_KEY, "false"));

        if(properties.containsKey(DEBUG_INTERVAL_KEY)) {
            int debugInterval = (Integer) (properties.get(DEBUG_INTERVAL_KEY));
            loop.scheduleAtFixedRate(this::print, debugInterval, debugInterval, TimeUnit.MILLISECONDS);
        }
    }

    void print() {
        StringBuilder data = new StringBuilder("\t" + getData());
        data.append("\tIN ");
        for (Map.Entry<Host, LinkedList<Connection<T>>> e : inConnections.entrySet()) {
            data.append(e.getKey().getAddress().getHostAddress().split("\\.")[3]).append(":")
                    .append(String.format("%,d", e.getValue().getFirst().getReceivedAppBytes())).append(" ");
        }
        data.append("\tOUT ");
        for (Map.Entry<Host, ConnectionState<T>> entry : outConnections.entrySet()) {
            data.append(entry.getKey().getAddress().getHostAddress().split("\\.")[3]).append(":")
                    .append(String.format("%,d", entry.getValue().getConnection().getSentAppBytes())).append(" ");
        }
        logger.info(data);
    }

    @Override
    protected void onOpenConnection(Host peer) {
        ConnectionState<T> conState = outConnections.get(peer);
        if (conState == null) {
            logger.debug("onOpenConnection creating connection to: " + peer);
            outConnections.put(peer, new ConnectionState<>(network.createConnection(peer, attributes, this)));
        } else if (conState.getState() == State.DISCONNECTING) {
            logger.debug("onOpenConnection reopening after close to: " + peer);
            conState.setState(State.DISCONNECTING_RECONNECT);
        } else
            logger.debug("onOpenConnection ignored: " + peer);
    }

    @Override
    protected void onSendMessage(T msg, Host peer, int connection) {
        logger.debug("SendMessage " + msg + " " + peer + " " + (connection == CONNECTION_IN ? "IN" : "OUT"));
        if (connection <= CONNECTION_OUT) {

            ConnectionState<T> conState = outConnections.computeIfAbsent(peer, k -> {
                logger.debug("onSendMessage creating connection to: " + peer);
                return new ConnectionState<>(network.createConnection(peer, attributes, this));
            });

            if (conState.getState() == State.CONNECTING) {
                conState.getQueue().add(msg);
            } else if (conState.getState() == State.CONNECTED) {
                sendWithListener(msg, peer, conState.getConnection());
            } else if (conState.getState() == State.DISCONNECTING) {
                conState.getQueue().add(msg);
                conState.setState(State.DISCONNECTING_RECONNECT);
            } else if (conState.getState() == State.DISCONNECTING_RECONNECT) {
                conState.getQueue().add(msg);
            }

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
        } else if (conState.getState() == State.CONNECTED) {
            throw new AssertionError("ConnectionUp in CONNECTED state: " + conn);
        } else if (conState.getState() == State.CONNECTING) {
            conState.setState(State.CONNECTED);
            conState.getQueue().forEach(m -> sendWithListener(m, conn.getPeer(), conn));
            conState.getQueue().clear();
            listener.deliverEvent(new OutConnectionUp(conn.getPeer()));
        } else { //DISCONNECTING OR DISCONNECTING_RECONNECT
            //do nothing
        }
    }

    @Override
    protected void onCloseConnection(Host peer, int connection) {
        logger.debug("CloseConnection " + peer);
        ConnectionState<T> conState = outConnections.get(peer);
        if (conState != null) {
            if (conState.getState() == State.CONNECTED || conState.getState() == State.CONNECTING
                    || conState.getState() == State.DISCONNECTING_RECONNECT) {
                conState.setState(State.DISCONNECTING);
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
        } else if (conState.getState() == State.CONNECTING) {
            throw new AssertionError("ConnectionDown in CONNECTING state: " + conn);
        } else if (conState.getState() == State.CONNECTED) {
            listener.deliverEvent(new OutConnectionDown(conn.getPeer(), cause));
        } else if (conState.getState() == State.DISCONNECTING_RECONNECT) {
            outConnections.put(conn.getPeer(), new ConnectionState<>(
                    network.createConnection(conn.getPeer(), attributes, this), conState.getQueue()));
        }
    }

    @Override
    protected void onOutboundConnectionFailed(Connection<T> conn, Throwable cause) {
        logger.debug("OutboundConnectionFailed " + conn.getPeer() + (cause != null ? (" " + cause) : ""));

        ConnectionState<T> conState = outConnections.remove(conn.getPeer());
        if (conState == null) {
            throw new AssertionError("ConnectionFailed with no conState: " + conn);
        } else if (conState.getState() == State.CONNECTING) {
            listener.deliverEvent(new OutConnectionFailed<>(conn.getPeer(), conState.getQueue(), cause));
        } else if (conState.getState() == State.DISCONNECTING_RECONNECT) {
            outConnections.put(conn.getPeer(), new ConnectionState<>(
                    network.createConnection(conn.getPeer(), attributes, this), conState.getQueue()));
        } else if (conState.getState() == State.CONNECTED) {
            throw new AssertionError("ConnectionFailed in state: " + conState.getState() + " - " + conn);
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
        } else {
            logger.debug("Extra InboundConnectionDown " + inConnList.size() + clientSocket);
        }
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
