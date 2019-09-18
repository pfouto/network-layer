package network;

/**
 * Interface that allows receiving notifications when the network layer detects node changes.
 */
public interface INodeListener {

    /**
     * Method called when a node is detected as down
     * @param peer The node that is now down
     */
    void nodeDown(Host peer);

    /**
     * Method called when a nod is detected as up
     * @param peer The node that is now up
     */
    void nodeUp(Host peer);

    /**
     * Method that is called when the connection to a node was lost and re-established
     * @param peerHost The node whose connection was re-established
     */
    void nodeConnectionReestablished(Host peerHost);
}
