package pt.unl.fct.di.novasys.network.messaging;

public class NetworkMessage
{
    public static final byte CTRL_MSG = 0;
    public static final byte APP_MSG = 1;

    public final byte code;
    public final Object payload;

    public NetworkMessage(byte code, Object payload){
        this.code = code;
        this.payload = payload;
    }

    @Override
    public String toString(){
        return "NetMsg " + code + " { " + payload.toString() + " }";
    }
}
