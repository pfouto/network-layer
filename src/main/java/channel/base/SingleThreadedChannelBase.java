package channel.base;

import channel.IChannel;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.DefaultThreadFactory;
import network.Connection;
import network.data.Host;
import network.listeners.MessageListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class SingleThreadedChannelBase<T, Y> implements IChannel<T>, MessageListener<Y> {

    private static final Logger logger = LogManager.getLogger(SingleThreadedChannelBase.class);

    protected final DefaultEventExecutor loop;

    public SingleThreadedChannelBase(String threadName) {
        loop = new DefaultEventExecutor(new DefaultThreadFactory(threadName));
    }

    @Override
    public void sendMessage(T msg, Host peer, int connection) {
        loop.execute(() -> onSendMessage(msg, peer, connection));
    }

    protected abstract void onSendMessage(T msg, Host peer, int connection);

    @Override
    public void closeConnection(Host peer, int  connection) {
        loop.execute(() -> onCloseConnection(peer, connection));
    }

    protected abstract void onCloseConnection(Host peer, int connection);

    @Override
    public void deliverMessage(Y msg, Connection<Y> conn) {
        loop.execute(() -> onDeliverMessage(msg, conn));
    }

    protected abstract void onDeliverMessage(Y msg, Connection<Y> conn);

    @Override
    public void openConnection(Host peer) {
        loop.execute(() -> onOpenConnection(peer));
    }

    protected abstract void onOpenConnection(Host peer);

}
