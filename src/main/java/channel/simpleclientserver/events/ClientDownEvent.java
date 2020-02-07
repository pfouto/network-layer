package channel.simpleclientserver.events;

import network.data.Host;

public class ClientDownEvent extends SimpleClientServerEvent {

    public static final short EVENT_ID = 202;

    private final Host client;
    private final Throwable cause;

    public ClientDownEvent(Host client, Throwable cause) {
        super(EVENT_ID);
        this.client = client;
        this.cause = cause;
    }

    @Override
    public String toString() {
        return "ClientDownEvent{" +
                "client=" + client +
                '}';
    }

    public Host getClient() {
        return client;
    }

    public Throwable getCause() {
        return cause;
    }
}
