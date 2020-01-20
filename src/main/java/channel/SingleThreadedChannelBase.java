package channel;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.DefaultThreadFactory;
import network.Connection;
import network.data.Host;
import network.listeners.InConnListener;
import network.listeners.MessageListener;
import network.listeners.OutConnListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class SingleThreadedChannelBase<T, Y> implements IChannel<T>, MessageListener<Y> {

    private static final Logger logger = LogManager.getLogger(SingleThreadedChannelBase.class);

    protected final DefaultEventExecutor loop;

    public SingleThreadedChannelBase() {
        loop = new DefaultEventExecutor(new DefaultThreadFactory(SingleThreadedChannelBase.class));
    }

    @Override
    public void sendMessage(T msg, Host peer) {
        loop.execute(() -> onSendMessage(msg, peer));
    }

    protected abstract void onSendMessage(T msg, Host peer);

    @Override
    public void closeConnection(Host peer) {
        loop.execute(() -> onCloseConnection(peer));
    }

    protected abstract void onCloseConnection(Host peer);

    @Override
    public void deliverMessage(Y msg, Connection<Y> conn) {
        loop.execute(() -> onDeliverMessage(msg, conn));
    }

    protected abstract void onDeliverMessage(Y msg, Connection<Y> conn);
}
