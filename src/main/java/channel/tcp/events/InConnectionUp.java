package channel.tcp.events;

import network.data.Host;

public class InConnectionUp extends TCPEvent {

    public static final short EVENT_ID = 2;

    private final Host node;

    @Override
    public String toString() {
        return "InConnectionUp{" +
                "node=" + node +
                '}';
    }

    public InConnectionUp(Host node) {
        super(EVENT_ID);
        this.node = node;
    }


    public Host getNode() {
        return node;
    }

}
