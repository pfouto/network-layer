package pt.unl.fct.di.novasys.channel.accrual;

import io.netty.util.concurrent.ScheduledFuture;
import pt.unl.fct.di.novasys.network.Connection;

public class InConnectionState<T> {

    private final Connection<T> connection;
    private ScheduledFuture<?> periodicHbTask;

    private long hbCounter = 0;

    public InConnectionState(Connection<T> conn){
        this.connection = conn;
    }

    public void setPeriodicHbTask(ScheduledFuture<?> periodicHbTask) {
        this.periodicHbTask = periodicHbTask;
    }

    public Connection<T> getConnection() {
        return connection;
    }

    public ScheduledFuture<?> getPeriodicHbTask() {
        return periodicHbTask;
    }

    public long getAndIncCounter(){
        return hbCounter++;
    }
}
