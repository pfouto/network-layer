package pt.unl.fct.di.novasys.channel.tcp.events;

import pt.unl.fct.di.novasys.network.data.Host;

/**
 * Triggered when a new outbound connection is established.
 */
public class OutConnectionUp extends TCPEvent {

    public static final short EVENT_ID = 5;

    private final Host node;

    @Override
    public String toString() {
        return "OutConnectionUp{" +
                "node=" + node +
                '}';
    }

    public OutConnectionUp(Host node) {
        super(EVENT_ID);
        this.node = node;
    }


    public Host getNode() {
        return node;
    }

}
