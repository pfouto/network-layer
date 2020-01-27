package channel.tcp.events;

import network.data.Host;

import java.util.List;

public class NodeDownEvent extends TCPEvent {

    public static final short EVENT_ID = 301;

    private final Host node;
    private final Throwable cause;

    @Override
    public String toString() {
        return "NodeDownEvent{" +
                "node=" + node +
                ", cause=" + cause +
                '}';
    }

    public NodeDownEvent(Host node, Throwable cause) {
        super(EVENT_ID);
        this.cause = cause;
        this.node = node;
    }

    public Throwable getCause() {
        return cause;
    }

    public Host getNode() {
        return node;
    }

}
