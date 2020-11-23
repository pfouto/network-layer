package pt.unl.fct.di.novasys.channel.ackos.messaging;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class AckosMessageSerializer<T> implements ISerializer<AckosMessage<T>> {

    private final ISerializer<T> innerSerializer;

    public AckosMessageSerializer(ISerializer<T> innerSerializer){
        this.innerSerializer = innerSerializer;
    }

    @Override
    public void serialize(AckosMessage<T> ackosMessage, ByteBuf out) throws IOException {
        out.writeInt(ackosMessage.getType().opCode);
        ackosMessage.getType().serializer.serialize(ackosMessage, out, innerSerializer);
    }

    @Override
    public AckosMessage<T> deserialize(ByteBuf in) throws IOException {
        AckosMessage.Type type = AckosMessage.Type.fromOpcode(in.readInt());
        return type.serializer.deserialize(in, innerSerializer);
    }
}
