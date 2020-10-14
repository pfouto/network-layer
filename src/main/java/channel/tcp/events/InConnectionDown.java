package channel.tcp.events;

import network.data.Host;

public class InConnectionDown extends TCPEvent {

    public static final short EVENT_ID = 1;

    private final Host node;
    private final Throwable cause;

    @Override
    public String toString() {
        return "InConnectionDown{" +
                "node=" + node +
                ", cause=" + cause +
                '}';
    }

    public InConnectionDown(Host node, Throwable cause) {
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
