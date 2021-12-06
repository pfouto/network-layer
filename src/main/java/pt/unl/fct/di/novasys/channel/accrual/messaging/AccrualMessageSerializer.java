package pt.unl.fct.di.novasys.channel.accrual.messaging;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class AccrualMessageSerializer<T> implements ISerializer<AccrualMessage<T>> {

    private final ISerializer<T> innerSerializer;

    public AccrualMessageSerializer(ISerializer<T> innerSerializer){
        this.innerSerializer = innerSerializer;
    }

    @Override
    public void serialize(AccrualMessage<T> accrualMessage, ByteBuf out) throws IOException {
        out.writeInt(accrualMessage.getType().opCode);
        accrualMessage.getType().serializer.serialize(accrualMessage, out, innerSerializer);
    }

    @Override
    public AccrualMessage<T> deserialize(ByteBuf in) throws IOException {
        AccrualMessage.Type type = AccrualMessage.Type.fromOpcode(in.readInt());
        return type.serializer.deserialize(in, innerSerializer);
    }
}
