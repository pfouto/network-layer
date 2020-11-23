package pt.unl.fct.di.novasys.network.messaging.control;

import io.netty.buffer.ByteBuf;

public class HeartbeatMessage extends ControlMessage
{
    public HeartbeatMessage()
    {
        super(Type.HEARTBEAT);
    }

    @Override
    public String toString(){
        return type.toString();
    }

    public static ControlMessageSerializer serializer = new ControlMessageSerializer<HeartbeatMessage>()
    {
        @Override
        public void serialize(HeartbeatMessage msg, ByteBuf out) { }

        @Override
        public HeartbeatMessage deserialize(ByteBuf in)
        {
            return new HeartbeatMessage();
        }
    };
}
