package pt.unl.fct.di.novasys.channel.accrual.messaging;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class AccrualAppMessage<T> extends AccrualMessage<T> {

    private final T payload;

    public AccrualAppMessage(T payload){
        super(Type.APP_MSG);
        this.payload = payload;
    }

    public T getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "AckosAppMessage{" +
                "payload=" + payload +
                '}';
    }

    public static final IAccrualSerializer serializer = new IAccrualSerializer<AccrualAppMessage>() {
        @Override
        public void serialize(AccrualAppMessage msg, ByteBuf out, ISerializer innerSerializer) throws IOException {
            innerSerializer.serialize(msg.payload, out);
        }

        @Override
        public AccrualAppMessage deserialize(ByteBuf in, ISerializer innerSerializer) throws IOException {
            Object payload = innerSerializer.deserialize(in);
            return new AccrualAppMessage(payload);
        }
    };
}
