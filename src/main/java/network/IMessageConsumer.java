package network;

/**
 * Interface that allows the receiving of a message T from the network layer.
 * @param <T> The type of message to be consumed
 * @author pfouto
 */
public interface IMessageConsumer<T> {

    /**
     * Method that is called to deliver a message from the network layer to the application
     * @param msgCode The code of the received message
     * @param msg The received message
     * @param from The sender of the message
     */
    void deliverMessage(short msgCode, T msg, Host from);
}
