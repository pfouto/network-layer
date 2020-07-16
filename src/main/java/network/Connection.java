package network;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import network.data.Attributes;
import network.data.Host;

/**
 * An interface that represents a connection to a host.
 * <p>
 * Connections are bounded to a specific message type
 *
 * @param <T> the message type which the connection operates over
 */
public interface Connection<T> {

    /**
     * Return the other end of connection
     * @return the host to which the connection is connected to
     */
    Host getPeer();

    /**
     * Return the attributes of the connection
     * @return the attributes of the connection
     */
    Attributes getAttributes();

    /**
     * Disconnect from the other end
     */
    void disconnect();

    /**
     * Check if the connection was initiated by the other end
     * @return true if the connection was initiated by the other end; false otherwise
     */
    boolean isInbound();

    /**
     * Check if the connection was initiated by this end
     * @return true if the connection was initiated by this end; false otherwise
     */
    boolean isOutbound();

    /**
     * Send a message over this connection
     *
     * @param msg the message
     * @param p a {@link Promise} to be executed when the message is effectively sent to the network
     */
    void sendMessage(T msg, Promise<Void> p);

    /**
     * Send a message over this connection
     *
     * @param msg the message
     */
    void sendMessage(T msg);
}
