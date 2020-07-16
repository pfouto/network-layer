package network.messaging.control;

import io.netty.buffer.ByteBuf;
import network.data.Attributes;

import java.io.IOException;

public class SecondHandshakeMessage extends ControlMessage {

    public final int magicNumber;
    public final Attributes attributes;

    public SecondHandshakeMessage(Attributes attrs) {
        this(ControlMessage.MAGIC_NUMBER, attrs);
    }

    public SecondHandshakeMessage(int magicNumber, Attributes attrs) {
        super(Type.SECOND_HS);
        this.magicNumber = magicNumber;
        this.attributes = attrs;
    }

    @Override
    public String toString() {
        return "SecondHSMessage{" +
                "attributes=" + attributes +
                "magicNumber=" + magicNumber +
                '}';
    }

    static ControlMessageSerializer serializer = new ControlMessageSerializer<SecondHandshakeMessage>() {

        public void serialize(SecondHandshakeMessage msg, ByteBuf out) throws IOException {
            out.writeInt(msg.magicNumber);
            Attributes.serializer.serialize(msg.attributes, out);
        }

        public SecondHandshakeMessage deserialize(ByteBuf in) throws IOException {
            int magicNumber = in.readInt();
            if (magicNumber != ControlMessage.MAGIC_NUMBER)
                throw new RuntimeException("Invalid magic number: " + magicNumber);
            Attributes attributes = Attributes.serializer.deserialize(in);
            return new SecondHandshakeMessage(magicNumber, attributes);
        }
    };
}
