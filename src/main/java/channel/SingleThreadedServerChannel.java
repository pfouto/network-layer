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

public abstract class SingleThreadedServerChannel<T, Y> extends SingleThreadedChannelBase<T,Y>
        implements InConnListener<Y> {

    private static final Logger logger = LogManager.getLogger(SingleThreadedServerChannel.class);

    @Override
    public void inboundConnectionUp(Connection<Y> con) {
        loop.execute(() -> onInboundConnectionUp(con));
    }

    protected abstract void onInboundConnectionUp(Connection<Y> con);

    @Override
    public void inboundConnectionDown(Connection<Y> con, Throwable cause) {
        loop.execute(() -> onInboundConnectionDown(con, cause));
    }

    protected abstract void onInboundConnectionDown(Connection<Y> con, Throwable cause);

    @Override
    public final void serverSocketBind(boolean success, Throwable cause) {
        loop.execute(() -> onServerSocketBind(success, cause));
    }

    protected abstract void onServerSocketBind(boolean success, Throwable cause);

    @Override
    public final void serverSocketClose(boolean success, Throwable cause) {
        loop.execute(() -> onServerSocketClose(success, cause));
    }

    protected abstract void onServerSocketClose(boolean success, Throwable cause);
}
