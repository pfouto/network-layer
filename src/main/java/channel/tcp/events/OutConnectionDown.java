package channel.tcp.events;

import network.data.Host;

import java.util.List;

public class OutConnectionDown extends TCPEvent {

    public static final short EVENT_ID = 301;

    private final Host node;
    private final Throwable cause;

    @Override
    public String toString() {
        return "OutConnectionDown{" +
                "node=" + node +
                ", cause=" + cause +
                '}';
    }

    public OutConnectionDown(Host node, Throwable cause) {
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
