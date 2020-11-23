package pt.unl.fct.di.novasys.channel.ackos.messaging;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class AckosAppMessage<T> extends AckosMessage<T> {

    private final long id;
    private final T payload;

    public AckosAppMessage(long id, T payload){
        super(Type.APP_MSG);
        this.id = id;
        this.payload = payload;
    }

    public T getPayload() {
        return payload;
    }

    public long getId() {
        return id;
    }

    @Override
    public String toString() {
        return "AckosAppMessage{" +
                "id=" + id +
                ", payload=" + payload +
                '}';
    }

    public static final IAckosSerializer serializer = new IAckosSerializer<AckosAppMessage>() {
        @Override
        public void serialize(AckosAppMessage msg, ByteBuf out, ISerializer innerSerializer) throws IOException {
            out.writeLong(msg.id);
            innerSerializer.serialize(msg.payload, out);
        }

        @Override
        public AckosAppMessage deserialize(ByteBuf in, ISerializer innerSerializer) throws IOException {
            long id = in.readLong();
            Object payload = innerSerializer.deserialize(in);
            return new AckosAppMessage(id, payload);
        }
    };
}
