package test;

import io.netty.buffer.ByteBuf;
import network.ISerializer;

import java.io.IOException;
import java.net.UnknownHostException;

public abstract class FTPMessage {
    interface FTPSerializer<T extends FTPMessage> extends ISerializer<T> {
    }

    public enum Type{
        HELLO(0, HelloMsg.serializer),
        PART(1, PartMsg.serializer),
        BYE(2, ByeMsg.serializer);

        public final int opcode;
        private final FTPSerializer<FTPMessage> serializer;

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

        Type(int opcode, FTPSerializer<FTPMessage> serializer) {
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

    FTPMessage(Type type) {
        this.type = type;
    }

    public static final ISerializer<FTPMessage> serializer = new ISerializer<FTPMessage>() {
        public void serialize(FTPMessage message, ByteBuf out) throws IOException {
            out.writeInt(message.type.opcode);
            message.type.serializer.serialize(message, out);
        }

        public FTPMessage deserialize(ByteBuf in) throws IOException {
            FTPMessage.Type type = FTPMessage.Type.fromOpcode(in.readInt());
            return type.serializer.deserialize(in);
        }
    };

}
