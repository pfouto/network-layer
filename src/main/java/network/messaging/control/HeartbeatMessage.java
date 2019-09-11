package network.messaging.control;

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

    @SuppressWarnings("Duplicates")
    public static ControlMessageSerializer serializer = new ControlMessageSerializer<HeartbeatMessage>()
    {
        public void serialize(HeartbeatMessage msg, ByteBuf out)
        {
        }

        public HeartbeatMessage deserialize(ByteBuf in)
        {
            return new HeartbeatMessage();
        }

        public int serializedSize(HeartbeatMessage msg)
        {
            return 0;
        }
    };
}
