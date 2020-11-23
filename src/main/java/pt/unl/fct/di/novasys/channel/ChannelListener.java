package pt.unl.fct.di.novasys.channel;

import pt.unl.fct.di.novasys.network.data.Host;

public interface ChannelListener<T> {

    void deliverMessage(T msg, Host from);

    void messageSent(T msg, Host to);

    void messageFailed(T msg, Host to, Throwable cause);

    void deliverEvent(ChannelEvent evt);
}
