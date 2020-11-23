package pt.unl.fct.di.novasys.channel.ackos;

import pt.unl.fct.di.novasys.channel.ackos.messaging.AckosAppMessage;
import pt.unl.fct.di.novasys.channel.ackos.messaging.AckosMessage;
import io.netty.util.concurrent.Promise;
import pt.unl.fct.di.novasys.network.Connection;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedList;
import java.util.Queue;

class OutConnectionContext<T> {

    private final Connection<AckosMessage<T>> connection;
    private final Queue<Pair<Long, T>> pending;
    private long counter;

    OutConnectionContext(Connection<AckosMessage<T>> connection) {
        this.connection = connection;
        pending = new LinkedList<>();
        counter = 0;
    }

    Connection<AckosMessage<T>> getConnection() {
        return connection;
    }

    Queue<Pair<Long, T>> getPending() {
        return pending;
    }

    void sendMessage(T msg, Promise<Void> p) {
        pending.add(Pair.of(++counter, msg));
        connection.sendMessage(new AckosAppMessage<>(counter, msg), p);
    }

    T ack(long id) {
        Pair<Long, T> poll = pending.poll();
        if (poll == null || poll.getKey() != id) throw new RuntimeException("Ack out of order: " + id + " " + poll);
        return poll.getValue();
    }
}
