package pt.unl.fct.di.novasys.channel.simpleclientserver.events;

import pt.unl.fct.di.novasys.network.data.Host;

public class ServerFailedEvent extends SimpleClientServerEvent {

    public static final short EVENT_ID = 205;

    private final Host server;
    private final Throwable cause;

    public ServerFailedEvent(Host server, Throwable cause) {
        super(EVENT_ID);
        this.server = server;
        this.cause = cause;
    }

    @Override
    public String toString() {
        return "ServerFailedEvent{" +
                "server=" + server +
                '}';
    }

    public Host getServer() {
        return server;
    }

    public Throwable getCause() {
        return cause;
    }
}
