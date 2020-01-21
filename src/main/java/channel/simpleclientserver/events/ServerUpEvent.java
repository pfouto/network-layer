package channel.simpleclientserver.events;

import network.data.Host;

public class ServerUpEvent extends SimpleClientServerEvent {

    public static final short EVENT_ID = 203;

    private final Host server;

    public ServerUpEvent(Host server) {
        super(EVENT_ID);
        this.server = server;
    }

    @Override
    public String toString() {
        return "ServerUpEvent{" +
                "server=" + server +
                '}';
    }

    public Host getServer() {
        return server;
    }
}
