package channel;

import network.data.Host;

public interface ChannelListener<T> {

    void deliverMessage(T msg, Host from);

    void deliverEvent(ChannelEvent<T> evt);
}
