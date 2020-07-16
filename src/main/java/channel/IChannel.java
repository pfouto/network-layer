package channel;

import network.data.Host;

/**
 * The channel interface
 * <p>
 * A channel is a communication abstraction that can offer different guarantees on a set of connections.
 * <p>
 * Channels are responsible to manage connections between peers or hosts.
 * <p>
 * Channels operate over a provided message type.
 *
 * @param <T> the message type which the channel operates over
 */
public interface IChannel<T> {

    /**
     * Send a message to given peer using the provided connection
     *
     * @param msg the message
     * @param peer the destination host
     * @param connection the connection numeric identifier
     */
    void sendMessage(T msg, Host peer, int connection);

    /**
     * Close the given connection to the given peer
     *
     * @param peer the host to which the connection is to be closed
     * @param connection the connection numeric identifier
     */
    void closeConnection(Host peer, int connection);

    /**
     * Open a connection to the given peer
     *
     * @param peer the host to which the connection is to be opened
     */
    void openConnection(Host peer);
}
