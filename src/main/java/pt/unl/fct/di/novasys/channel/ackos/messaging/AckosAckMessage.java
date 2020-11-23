package pt.unl.fct.di.novasys.channel.ackos.messaging;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

public class AckosAckMessage<T> extends AckosMessage<T> {

    private final long id;

    public AckosAckMessage(long id) {
        super(Type.ACK);
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public static final IAckosSerializer serializer = new IAckosSerializer<AckosAckMessage>() {
        @Override
        public void serialize(AckosAckMessage msg, ByteBuf out, ISerializer innerSerializer) {
            out.writeLong(msg.id);
        }

        @Override
        public AckosAckMessage deserialize(ByteBuf in, ISerializer innerSerializer) {
            return new AckosAckMessage(in.readLong());
        }
    };

}
