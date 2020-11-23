package pt.unl.fct.di.novasys.channel.base;

import pt.unl.fct.di.novasys.network.Connection;
import pt.unl.fct.di.novasys.network.listeners.InConnListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class SingleThreadedServerChannel<T, Y> extends SingleThreadedChannel<T,Y>
        implements InConnListener<Y> {

    private static final Logger logger = LogManager.getLogger(SingleThreadedServerChannel.class);

    public SingleThreadedServerChannel(String threadName){
        super(threadName);
    }

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
