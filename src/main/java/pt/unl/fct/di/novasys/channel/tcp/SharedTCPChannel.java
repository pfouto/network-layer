package pt.unl.fct.di.novasys.channel.tcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.channel.ChannelListener;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SharedTCPChannel<T> extends TCPChannel<T> {

    private static final Logger logger = LogManager.getLogger(SharedTCPChannel.class);

    private final Map<Host, Map<Integer, Object>> connections;

    public final static String NAME = "SharedTCPChannel";

    public SharedTCPChannel(ISerializer<T> serializer, ChannelListener<T> list, Properties properties) throws IOException {
        super(serializer, list, properties);
        connections = new ConcurrentHashMap<>();
    }

    @Override
    protected void onOpenConnection(Host peer, int connection) {
        super.onOpenConnection(peer, connection);
        addConnection(peer, connection);
    }

    @Override
    protected void onCloseConnection(Host peer, int connection) {
        Map<Integer, Object> openConnections = removeConnection(peer, connection);

        if (openConnections == null || openConnections.isEmpty()) {
            super.onCloseConnection(peer, connection);
        }
    }

    /* --------------------------- Helpers --------------------------- */

    private Map<Integer, Object> addConnection(Host peer, int connection) {
        Map<Integer, Object> openConnections = connections.computeIfAbsent(peer, k -> new ConcurrentHashMap<>());
        openConnections.put(connection, new Object());
        return openConnections;
    }

    private Map<Integer, Object> removeConnection(Host peer, int connection) {
        Map<Integer, Object> openConnections = connections.get(peer);
        if (openConnections != null) {
            openConnections.remove(connection);
        }
        return openConnections;
    }
}
