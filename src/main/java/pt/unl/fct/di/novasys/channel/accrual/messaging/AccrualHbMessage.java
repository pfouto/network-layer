package pt.unl.fct.di.novasys.channel.accrual.messaging;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

public class AccrualHbMessage<T> extends AccrualMessage<T> {

    private final long counter;

    public AccrualHbMessage(long counter) {
        super(Type.HB);
        this.counter = counter;
    }

    public long getCounter() {
        return counter;
    }

    public static final IAccrualSerializer serializer = new IAccrualSerializer<AccrualHbMessage>() {
        @Override
        public void serialize(AccrualHbMessage msg, ByteBuf out, ISerializer innerSerializer) {
            out.writeLong(msg.counter);
        }

        @Override
        public AccrualHbMessage deserialize(ByteBuf in, ISerializer innerSerializer) {
            return new AccrualHbMessage(in.readLong());
        }
    };

}
