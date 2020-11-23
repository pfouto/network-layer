package pt.unl.fct.di.novasys.network.listeners;

import pt.unl.fct.di.novasys.network.Connection;

/**
 * Interface that allows the receiving of a message T from the network layer.
 * @param <T> The type of message to be consumed
 * @author pfouto
 */
public interface MessageListener<T> {

    /**
     * Method that is called to deliver a message from the network layer to the application
     * @param msg The received message
     */
    void deliverMessage(T msg, Connection<T> conn);
}
