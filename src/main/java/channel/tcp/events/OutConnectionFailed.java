package channel.tcp.events;

import network.data.Host;

import java.util.List;
import java.util.Queue;

public class OutConnectionFailed<T>  extends TCPEvent {

    public static final short EVENT_ID = 4;

    private final Host node;
    private final Queue<T> pendingMessages;
    private final Throwable cause;

    @Override
    public String toString() {
        return "OutConnectionFailed{" +
                "node=" + node +
                ", pendingMessages=" + pendingMessages +
                ", cause=" + cause +
                '}';
    }

    public OutConnectionFailed(Host node, Queue<T> pendingMessages, Throwable cause) {
        super(EVENT_ID);
        this.cause = cause;
        this.node = node;
        this.pendingMessages = pendingMessages;
    }

    public Throwable getCause() {
        return cause;
    }

    public Host getNode() {
        return node;
    }

    public Queue<T> getPendingMessages() {
        return pendingMessages;
    }
}
