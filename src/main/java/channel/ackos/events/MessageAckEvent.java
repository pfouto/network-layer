package channel.ackos.events;

import network.data.Host;

public class MessageAckEvent<T> extends AckosEvent<T>{

    public static final short EVENT_ID = 101;

    private final Host node;
    private final T message;

    public MessageAckEvent(Host node, T message) {
        super(EVENT_ID);
        this.node = node;
        this.message = message;
    }

    public Host getNode() {
        return node;
    }

    @Override
    public String toString() {
        return "MessageDeliveredEvent{" +
                "node=" + node +
                ", message=" + message +
                '}';
    }

    public T getMessage() {
        return message;
    }
}
