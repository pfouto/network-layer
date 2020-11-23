package pt.unl.fct.di.novasys.channel.tcp.events;

import pt.unl.fct.di.novasys.channel.tcp.ConnectionState;
import pt.unl.fct.di.novasys.network.Connection;
import pt.unl.fct.di.novasys.network.data.Host;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Triggered periodically if enabled. Contains metrics about every connection managed by the channel.
 */
public class ChannelMetrics extends TCPEvent {

    public static final short EVENT_ID = 6;

    List<ConnectionMetrics> inConnections;
    List<ConnectionMetrics> outConnections;
    List<ConnectionMetrics> oldInConnections;
    List<ConnectionMetrics> oldOutConnections;

    public <T> ChannelMetrics(List<Pair<Host, Connection<T>>> oldIn, List<Pair<Host, ConnectionState<T>>> oldOUt,
                              Map<Host, LinkedList<Connection<T>>> in,
                              Map<Host, ConnectionState<T>> out) {
        super(EVENT_ID);

        inConnections = new LinkedList<>();
        in.forEach((h, v) -> v.forEach(c -> inConnections.add(new ConnectionMetrics(h, c.getReceivedAppBytes(),
                c.getSentAppBytes(), c.getReceivedControlBytes(), c.getSentControlBytes(), c.getReceivedAppMessages(),
                c.getSentAppMessages(), c.getReceivedControlMessages(), c.getSentControlMessages(), true))));

        outConnections = new LinkedList<>();
        out.forEach((h, cc) -> {
            Connection<T> c = cc.getConnection();
            outConnections.add(new ConnectionMetrics(h, c.getReceivedAppBytes(),
                    c.getSentAppBytes(), c.getReceivedControlBytes(), c.getSentControlBytes(),
                    c.getReceivedAppMessages(), c.getSentAppMessages(), c.getReceivedControlMessages(),
                    c.getSentControlMessages(), true));
        });

        oldInConnections = new LinkedList<>();
        oldIn.forEach(p -> {
            Connection<T> c = p.getRight();
            oldInConnections.add(new ConnectionMetrics(p.getLeft(), c.getReceivedAppBytes(), c.getSentAppBytes(),
                    c.getReceivedControlBytes(), c.getSentControlBytes(), c.getReceivedAppMessages(),
                    c.getSentAppMessages(), c.getReceivedControlMessages(), c.getSentControlMessages(), true));
        });

        oldOutConnections = new LinkedList<>();
        oldOUt.forEach(p -> {
            Connection<T> c = p.getRight().getConnection();
            oldOutConnections.add(new ConnectionMetrics(p.getLeft(), c.getReceivedAppBytes(),
                    c.getSentAppBytes(), c.getReceivedControlBytes(), c.getSentControlBytes(),
                    c.getReceivedAppMessages(), c.getSentAppMessages(), c.getReceivedControlMessages(),
                    c.getSentControlMessages(), true));
        });
    }

    @Override
    public String toString() {
        return "ChannelMetrics{" +
                "inConnections=" + inConnections +
                ", outConnections=" + outConnections +
                ", oldInConnections=" + oldInConnections +
                ", oldOutConnections=" + oldOutConnections +
                '}';
    }

    public List<ConnectionMetrics> getInConnections() {
        return inConnections;
    }

    public List<ConnectionMetrics> getOutConnections() {
        return outConnections;
    }

    public List<ConnectionMetrics> getOldInConnections() {
        return oldInConnections;
    }

    public List<ConnectionMetrics> getOldOutConnections() {
        return oldOutConnections;
    }


    public static class ConnectionMetrics {
        private final Host peer;
        private final long receivedAppBytes, sentAppBytes, receivedControlBytes, sentControlBytes;
        private final long receivedAppMessages, sentAppMessages, receivedControlMessages, sentControlMessages;
        private final boolean active;

        public ConnectionMetrics(Host peer, long receivedAppBytes, long sentAppBytes, long receivedControlBytes,
                                 long sentControlBytes, long receivedAppMessages, long sentAppMessages,
                                 long receivedControlMessages, long sentControlMessages, boolean active) {
            this.peer = peer;
            this.receivedAppBytes = receivedAppBytes;
            this.sentAppBytes = sentAppBytes;
            this.receivedControlBytes = receivedControlBytes;
            this.sentControlBytes = sentControlBytes;
            this.receivedAppMessages = receivedAppMessages;
            this.sentAppMessages = sentAppMessages;
            this.receivedControlMessages = receivedControlMessages;
            this.sentControlMessages = sentControlMessages;
            this.active = active;
        }

        public long getReceivedAppBytes() {
            return receivedAppBytes;
        }

        public long getSentAppBytes() {
            return sentAppBytes;
        }

        public long getReceivedControlBytes() {
            return receivedControlBytes;
        }

        public long getSentControlBytes() {
            return sentControlBytes;
        }

        public long getReceivedAppMessages() {
            return receivedAppMessages;
        }

        public long getSentAppMessages() {
            return sentAppMessages;
        }

        public long getReceivedControlMessages() {
            return receivedControlMessages;
        }

        public long getSentControlMessages() {
            return sentControlMessages;
        }

        public boolean isActive() {
            return active;
        }

        public Host getPeer() {
            return peer;
        }

        @Override
        public String toString() {
            return "Metrics " +
                    peer +
                    "{ receivedAppBytes=" + receivedAppBytes +
                    ", sentAppBytes=" + sentAppBytes +
                    ", receivedControlBytes=" + receivedControlBytes +
                    ", sentControlBytes=" + sentControlBytes +
                    ", receivedAppMessages=" + receivedAppMessages +
                    ", sentAppMessages=" + sentAppMessages +
                    ", receivedControlMessages=" + receivedControlMessages +
                    ", sentControlMessages=" + sentControlMessages +
                    ", active=" + active +
                    '}';
        }
    }
}
