package network;

import java.util.Iterator;

public interface INetwork
{
    Host myHost();

    void addPeer(Host peerHost);

    void removePeer(Host peerHost);

    boolean isConnectionActive(Host peerHost);

    void sendMessage(byte msgCode, Object payload, Host to, boolean newChannel);

    void sendMessage(byte msgCode, Object msg, Host to);

    void broadcastMessage(byte msgCode, Object msg, Iterator<Host> targets);

    void registerConsumer(byte messageCode, IMessageConsumer consumer);

    void registerSerializer(byte messageCode, ISerializer serializer);

    void registerNodeListener(INodeListener listener);

    int getnSent();
}
