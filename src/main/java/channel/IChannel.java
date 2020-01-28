package channel;

import network.data.Host;

public interface IChannel<T> {

    void sendMessage(T msg, Host peer, int mode);

    void closeConnection(Host peer);
}
