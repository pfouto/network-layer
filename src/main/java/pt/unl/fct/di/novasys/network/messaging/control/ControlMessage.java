package pt.unl.fct.di.novasys.network.messaging.control;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public abstract class ControlMessage {

    public final static int MAGIC_NUMBER = 0x79676472;

    interface ControlMessageSerializer<T extends ControlMessage> extends ISerializer<T> {
    }

    public enum Type {
        HEARTBEAT(0, HeartbeatMessage.serializer),
        FIRST_HS(1, FirstHandshakeMessage.serializer),
        SECOND_HS(2, SecondHandshakeMessage.serializer),
        INVALID_ATTR(3, InvalidAttributesMessage.serializer);

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

    ControlMessage(Type type) {
        this.type = type;
    }

    public static final ISerializer<ControlMessage> serializer = new ISerializer<ControlMessage>() {
        @Override
        public void serialize(ControlMessage message, ByteBuf out) throws IOException {
            out.writeInt(message.type.opcode);
            message.type.serializer.serialize(message, out);
        }

        @Override
        public ControlMessage deserialize(ByteBuf in) throws IOException {
            Type type = Type.fromOpcode(in.readInt());
            return type.serializer.deserialize(in);
        }
    };
}