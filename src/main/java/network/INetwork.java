package network;

import java.util.Iterator;

/**
 * Represents the network layer.
 *
 * @author pfouto
 */
public interface INetwork {

    /**
     * Get information about the local node
     *
     * @return An object with the IP and port of the local node
     */
    Host myHost();

    /**
     * Adds a new peer connection to the network layer.
     * This method immediately attempts to connect to the peer.
     * This method needs to be called before attempting to send messages to the peer.
     *
     * @param peerHost The peer to create a connection to.
     */
    void addPeer(Host peerHost);

    /**
     * Removes the peer from the network layer, which closes any open connections.
     *
     * @param peerHost The peer to remove from the network layer
     */
    void removePeer(Host peerHost);

    /**
     * Checks if the is an open connection to a peer
     *
     * @param peerHost The peer
     * @return Whether there is an open connection to the peer.
     */
    boolean isConnectionActive(Host peerHost);

    /**
     * Sends a message to a peer, possibly using a dedicated channel
     *
     * @param msgCode    The code of the message to send
     * @param payload    The message to send
     * @param to         The peer to send the message to
     * @param newChannel Whether to create a dedicated channel to send the message or not
     */
    void sendMessage(short msgCode, Object payload, Host to, boolean newChannel);

    /**
     * Sends a message to a peer
     *
     * @param msgCode The code of the message to send
     * @param msg     The message to send
     * @param to      The peer to send the message to
     */
    void sendMessage(short msgCode, Object msg, Host to);

    /**
     * Send a message to multiple peers
     *
     * @param msgCode The code of the message to send
     * @param msg     The message to send
     * @param targets The list of peers to send the message to
     */
    void broadcastMessage(short msgCode, Object msg, Iterator<Host> targets);

    /**
     * Registers a new consumer to handle messages with a specific code
     *
     * @param messageCode The message to register
     * @param consumer    The consumer to handle messages with the given code
     */
    void registerConsumer(short messageCode, IMessageConsumer consumer);

    /**
     * Registers the (de)serializer to be used when sending or receiving messages with a specific code
     *
     * @param messageCode The message code to register
     * @param serializer  The object used to (de)serialize messages with the given code
     */
    void registerSerializer(short messageCode, ISerializer serializer);

    /**
     * Registers the listener to receive updates when peer state changes
     *
     * @param listener The listener to register.
     */
    void registerNodeListener(INodeListener listener);

}
