package network;

import java.util.Properties;

class NetworkConfiguration
{

    final String LISTEN_ADDRESS;
    final String LISTEN_INTERFACE;
    final short LISTEN_BASE_PORT;
    final int CONNECT_TIMEOUT_MILLIS;
    final int HEARTBEAT_INTERVAL_MILLIS;
    final int IDLE_TIMEOUT_MILLIS;
    final int RECONNECT_ATTEMPTS_BEFORE_DOWN;
    final int RECONNECT_INTERVAL_BEFORE_DOWN_MILLIS;
    final int RECONNECT_INTERVAL_AFTER_DOWN_MILLIS;

    NetworkConfiguration(Properties props){
        LISTEN_ADDRESS = props.getProperty("listen_address");
        LISTEN_INTERFACE = props.getProperty("listen_interface");
        LISTEN_BASE_PORT = Short.valueOf(props.getProperty("listen_base_port"));
        CONNECT_TIMEOUT_MILLIS = Integer.parseInt(props.getProperty("connect_timeout_millis"));
        HEARTBEAT_INTERVAL_MILLIS = Integer.valueOf(props.getProperty("heartbeat_interval_millis"));
        IDLE_TIMEOUT_MILLIS = Integer.valueOf(props.getProperty("idle_timeout_millis"));
        RECONNECT_ATTEMPTS_BEFORE_DOWN = Integer.valueOf(props.getProperty("reconnect_attempts_before_down"));
        RECONNECT_INTERVAL_BEFORE_DOWN_MILLIS = Integer.valueOf(props.getProperty("reconnect_interval_before_down_millis"));
        RECONNECT_INTERVAL_AFTER_DOWN_MILLIS = Integer.valueOf(props.getProperty("reconnect_interval_after_down_millis"));
    }

}
