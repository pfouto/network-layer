package pt.unl.fct.di.novasys.channel.base;

import pt.unl.fct.di.novasys.network.Connection;
import pt.unl.fct.di.novasys.network.listeners.InConnListener;
import pt.unl.fct.di.novasys.network.listeners.OutConnListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class SingleThreadedBiChannel<T, Y> extends SingleThreadedChannel<T,Y>
        implements OutConnListener<Y>, InConnListener<Y> {

    private static final Logger logger = LogManager.getLogger(SingleThreadedBiChannel.class);

    public SingleThreadedBiChannel(String threadName){
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

    @Override
    public void outboundConnectionUp(Connection<Y> con) {
        loop.execute(() -> onOutboundConnectionUp(con));
    }

    protected abstract void onOutboundConnectionUp(Connection<Y> conn);

    @Override
    public void outboundConnectionDown(Connection<Y> con, Throwable cause) {
        loop.execute(() -> onOutboundConnectionDown(con, cause));
    }

    protected abstract void onOutboundConnectionDown(Connection<Y> conn, Throwable cause);

    @Override
    public void outboundConnectionFailed(Connection<Y> con, Throwable cause) {
        loop.execute(() -> onOutboundConnectionFailed(con, cause));
    }

    protected abstract void onOutboundConnectionFailed(Connection<Y> conn, Throwable cause);

}
