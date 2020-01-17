package network;

import network.data.Attributes;
import network.data.Host;

public interface Connection<T> {

    Host getPeer();

    Attributes getAttributes();

    void sendMessage(T msg);

    void disconnect();

    boolean isInbound();

    boolean isOutbound();

}
