package network.messaging;

public class NetworkMessage
{
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
