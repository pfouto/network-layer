package pt.unl.fct.di.novasys.channel.ackos.events;

import pt.unl.fct.di.novasys.network.data.Host;

import java.util.List;

public class NodeDownEvent<T> extends AckosEvent {

    public static final short EVENT_ID = 102;

    private final Host node;
    private final List<T> messages;
    private final Throwable cause;

    @Override
    public String toString() {
        return "NodeDownEvent{" +
                "node=" + node +
                ", messages=" + messages +
                ", cause=" + cause +
                '}';
    }

    public NodeDownEvent(Host node, List<T> messages, Throwable cause) {
        super(EVENT_ID);
        this.cause = cause;
        this.node = node;
        this.messages = messages;
    }

    public Throwable getCause() {
        return cause;
    }

    public Host getNode() {
        return node;
    }

    public List<T> getMessages() {
        return messages;
    }


}
