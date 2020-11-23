package pt.unl.fct.di.novasys.channel.base;

import pt.unl.fct.di.novasys.network.Connection;
import pt.unl.fct.di.novasys.network.listeners.OutConnListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class SingleThreadedClientChannel<T, Y> extends SingleThreadedChannel<T,Y>
        implements OutConnListener<Y> {

    private static final Logger logger = LogManager.getLogger(SingleThreadedClientChannel.class);

    public SingleThreadedClientChannel(String threadName){
        super(threadName);
    }

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
