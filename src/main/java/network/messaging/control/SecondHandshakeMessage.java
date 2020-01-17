package network.messaging.control;

import io.netty.buffer.ByteBuf;

public class SecondHandshakeMessage extends ControlMessage {

    public final int magicNumber;

    public SecondHandshakeMessage() {
        this(ControlMessage.MAGIC_NUMBER);
    }

    public SecondHandshakeMessage(int magicNumber) {
        super(Type.SECOND_HS);
        this.magicNumber = magicNumber;
    }

    @Override
    public String toString() {
        return "SecondHSMessage{" +
                "magicNumber=" + magicNumber +
                '}';
    }

    static ControlMessageSerializer serializer = new ControlMessageSerializer<SecondHandshakeMessage>() {

        public void serialize(SecondHandshakeMessage msg, ByteBuf out) {
            out.writeInt(msg.magicNumber);
        }

        public SecondHandshakeMessage deserialize(ByteBuf in) {
            int magicNumber = in.readInt();
            if(magicNumber != ControlMessage.MAGIC_NUMBER)
                throw new RuntimeException("Invalid magic number: " + magicNumber);
            return new SecondHandshakeMessage(magicNumber);
        }
    };
}
