package pt.unl.fct.di.novasys.channel.accrual;

import pt.unl.fct.di.novasys.network.Connection;

import java.util.LinkedList;
import java.util.Queue;

public class OutConnectionState<T> {

    public enum State {CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTING_RECONNECT}

    private final Connection<T> connection;
    private State state;
    private final Queue<T> queue;

    public OutConnectionState(Connection<T> conn) {
        this.connection = conn;
        this.state = State.CONNECTING;
        this.queue = new LinkedList<>();
    }

    public OutConnectionState(Connection<T> conn, Queue<T> initialQueue) {
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
