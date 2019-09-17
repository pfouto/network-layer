package network.messaging.control;

import io.netty.buffer.ByteBuf;
import network.ISerializer;

import java.net.UnknownHostException;

public abstract class ControlMessage
{

    public final static short MSG_CODE = 0;

    interface ControlMessageSerializer<T extends ControlMessage> extends ISerializer<T> {}

    public enum Type {
        HEARTBEAT(0, HeartbeatMessage.serializer),
        FIRST_HS(1, FirstHandshakeMessage.serializer);

        public final int opcode;
        private final ControlMessageSerializer<ControlMessage> serializer;

        private static final Type[] opcodeIdx;
        static {
            int maxOpcode = -1;
            for (Type type : Type.values())
                maxOpcode = Math.max(maxOpcode, type.opcode);
            opcodeIdx = new Type[maxOpcode + 1];
            for (Type type : Type.values()) {
                if (opcodeIdx[type.opcode] != null)
                    throw new IllegalStateException("Duplicate opcode");
                opcodeIdx[type.opcode] = type;
            }
        }

        Type(int opcode, ControlMessageSerializer<ControlMessage> serializer) {
            this.opcode = opcode;
            this.serializer = serializer;
        }

        @SuppressWarnings("Duplicates")
        public static Type fromOpcode(int opcode) {
            if (opcode >= opcodeIdx.length || opcode < 0)
                throw new AssertionError(String.format("Unknown opcode %d", opcode));
            Type t = opcodeIdx[opcode];
            if (t == null)
                throw new AssertionError(String.format("Unknown opcode %d", opcode));
            return t;
        }
    }

    public final Type type;
    private volatile int serializedSize = -1;

    ControlMessage(Type type){
        this.type = type;
    }

    public static final ISerializer<ControlMessage> serializer = new ISerializer<ControlMessage>()
    {
        @Override
        public void serialize(ControlMessage message, ByteBuf out)
        {
            out.writeInt(message.type.opcode);
            message.type.serializer.serialize(message, out);
        }

        @Override
        public ControlMessage deserialize(ByteBuf in) throws UnknownHostException
        {
            Type type = Type.fromOpcode(in.readInt());
            return type.serializer.deserialize(in);
        }

        @SuppressWarnings("Duplicates")
        @Override
        public int serializedSize(ControlMessage message)
        {
            if(message.serializedSize != -1)
                return message.serializedSize; //only calc once
            int size = 4; // type;
            size += message.type.serializer.serializedSize(message);
            message.serializedSize = size;
            return size;
        }
    };


}
