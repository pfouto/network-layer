package pt.unl.fct.di.novasys.channel.simpleclientserver.events;

import pt.unl.fct.di.novasys.network.data.Host;

public class ClientUpEvent extends SimpleClientServerEvent {

    public static final short EVENT_ID = 201;

    private final Host client;

    public ClientUpEvent(Host client) {
        super(EVENT_ID);
        this.client = client;
    }

    @Override
    public String toString() {
        return "ClientUpEvent{" +
                "client=" + client +
                '}';
    }

    public Host getClient() {
        return client;
    }
}
