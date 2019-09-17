package network;

import java.util.Iterator;

public interface INetwork
{
    Host myHost();

    void addPeer(Host peerHost);

    void removePeer(Host peerHost);

    boolean isConnectionActive(Host peerHost);

    void sendMessage(short msgCode, Object payload, Host to, boolean newChannel);

    void sendMessage(short msgCode, Object msg, Host to);

    void broadcastMessage(short msgCode, Object msg, Iterator<Host> targets);

    void registerConsumer(short messageCode, IMessageConsumer consumer);

    void registerSerializer(short messageCode, ISerializer serializer);

    void registerNodeListener(INodeListener listener);

    int getnSent();
}
