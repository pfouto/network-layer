package channel;

import network.data.Host;

public interface IChannel<T> {

    void sendMessage(T msg, Host peer, int connection);

    void closeConnection(Host peer, int connection);

    void openConnection(Host peer);
}
