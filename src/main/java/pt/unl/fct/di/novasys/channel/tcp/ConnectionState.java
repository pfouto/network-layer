package pt.unl.fct.di.novasys.channel.tcp;

import pt.unl.fct.di.novasys.network.Connection;

import java.util.LinkedList;
import java.util.Queue;

public class ConnectionState<T> {

    public enum State {CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTING_RECONNECT}

    private final Connection<T> connection;
    private State state;
    private final Queue<T> queue;

    public ConnectionState(Connection<T> conn) {
        this.connection = conn;
        this.state = State.CONNECTING;
        this.queue = new LinkedList<>();
    }

    public ConnectionState(Connection<T> conn, Queue<T> initialQueue) {
        this.connection = conn;
        this.state = State.CONNECTING;
        this.queue = new LinkedList<>(initialQueue);
    }

    public Connection<T> getConnection() {
        return connection;
    }

    public Queue<T> getQueue() {
        return queue;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
}
