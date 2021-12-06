package pt.unl.fct.di.novasys.network.messaging.control;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.data.Attributes;

import java.io.IOException;

public class SecondHandshakeMessage extends ControlMessage {

    static ControlMessageSerializer serializer = new ControlMessageSerializer<SecondHandshakeMessage>() {

        public void serialize(SecondHandshakeMessage msg, ByteBuf out) throws IOException {
            out.writeInt(msg.magicNumber);
            Attributes.serializer.serialize(msg.attributes, out);
        }

        public SecondHandshakeMessage deserialize(ByteBuf in) throws IOException {
            int magicNumber = in.readInt();
            if (magicNumber != MAGIC_NUMBER)
                throw new RuntimeException("Invalid magic number: " + magicNumber);
            Attributes attributes = Attributes.serializer.deserialize(in);
            return new SecondHandshakeMessage(magicNumber, attributes);
        }
    };
    public final int magicNumber;
    public final Attributes attributes;

    public SecondHandshakeMessage(Attributes attrs) {
        this(MAGIC_NUMBER, attrs);
    }

    public SecondHandshakeMessage(int magicNumber, Attributes attrs) {
        super(Type.SECOND_HS);
        this.magicNumber = magicNumber;
        this.attributes = attrs;
    }

    @Override
    public String toString() {
        return "SecondHandshakeMessage{" +
                "attributes=" + attributes +
                "magicNumber=" + magicNumber +
                '}';
    }
}
