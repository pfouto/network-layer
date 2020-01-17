package network.listeners;

import network.Connection;

public interface OutConnListener<T> {

    void outboundConnectionUp(Connection<T> con);

    void outboundConnectionDown(Connection<T> con, Throwable cause);

    void outboundConnectionFailed(Connection<T> con, Throwable cause);

}