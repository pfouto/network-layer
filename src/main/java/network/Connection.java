package network;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import network.data.Attributes;
import network.data.Host;

public interface Connection<T> {

    Host getPeer();

    Attributes getAttributes();

    void disconnect();

    boolean isInbound();

    boolean isOutbound();

    void sendMessage(T msg, GenericFutureListener<ChannelFuture> f);

    void sendMessage(T msg);
}
