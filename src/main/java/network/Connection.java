package network;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import network.data.Attributes;
import network.data.Host;

public interface Connection<T> {

    Host getPeer();

    Attributes getPeerAttributes();

    Attributes getSelfAttributes();

    void disconnect();

    boolean isInbound();

    boolean isOutbound();

    void sendMessage(T msg, Promise<Void> p);

    void sendMessage(T msg);

    EventLoop getLoop();

    long getSentAppBytes();

    long getSentAppMessages();

    long getSentControlBytes();

    long getSentControlMessages();

    long getReceivedAppBytes();

    long getReceivedAppMessages();

    long getReceivedControlBytes();

    long getReceivedControlMessages();

}
