package channel;

import network.data.Host;

public interface IChannel<T> {

    void sendMessage(T msg, Host peer);

    void closeConnection(Host peer);
}
