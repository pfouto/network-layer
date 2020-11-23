package pt.unl.fct.di.novasys.channel.ackos.messaging;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public abstract class AckosMessage<T> {

    public enum Type {
        APP_MSG(0, AckosAppMessage.serializer),
        ACK(1, AckosAckMessage.serializer);

        public final int opCode;
        public final IAckosSerializer<AckosMessage> serializer;
        private static final Type[] opcodeIdx;

        static {
            int maxOpcode = -1;
            for (Type type : Type.values())
                maxOpcode = Math.max(maxOpcode, type.opCode);
            opcodeIdx = new Type[maxOpcode + 1];
            for (Type type : Type.values()) {
                if (opcodeIdx[type.opCode] != null)
                    throw new IllegalStateException("Duplicate opcode");
                opcodeIdx[type.opCode] = type;
            }
        }

        Type(int opCode, IAckosSerializer<AckosMessage> serializer){
            this.opCode = opCode;
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

    private Type type;

    public AckosMessage(Type type){
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public interface IAckosSerializer<T extends AckosMessage> {
        void serialize(T msg, ByteBuf out, ISerializer innerSerializer) throws IOException;
        T deserialize(ByteBuf in, ISerializer innerSerializer) throws IOException;
    }
}
