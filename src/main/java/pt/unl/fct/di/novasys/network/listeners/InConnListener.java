package pt.unl.fct.di.novasys.network.listeners;

import pt.unl.fct.di.novasys.network.Connection;

public interface InConnListener<T> {

    void inboundConnectionUp(Connection<T> con);

    void inboundConnectionDown(Connection<T> con, Throwable cause);

    void serverSocketBind(boolean success, Throwable cause);

    void serverSocketClose(boolean success, Throwable cause);
}