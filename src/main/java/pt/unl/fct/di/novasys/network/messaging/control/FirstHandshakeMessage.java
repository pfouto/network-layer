package pt.unl.fct.di.novasys.network.messaging.control;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.data.Attributes;

import java.io.IOException;

public class FirstHandshakeMessage extends ControlMessage {

    static ControlMessageSerializer serializer = new ControlMessageSerializer<FirstHandshakeMessage>() {

        public void serialize(FirstHandshakeMessage msg, ByteBuf out) throws IOException {
            out.writeInt(msg.magicNumber);
            Attributes.serializer.serialize(msg.attributes, out);
        }

        public FirstHandshakeMessage deserialize(ByteBuf in) throws IOException {
            int magicNumber = in.readInt();
            if (magicNumber != MAGIC_NUMBER)
                throw new RuntimeException("Invalid magic number: " + magicNumber);
            Attributes attributes = Attributes.serializer.deserialize(in);
            return new FirstHandshakeMessage(magicNumber, attributes);
        }
    };
    public final int magicNumber;
    public final Attributes attributes;

    public FirstHandshakeMessage(Attributes attributes) {
        this(MAGIC_NUMBER, attributes);
    }

    public FirstHandshakeMessage(int magicNumber, Attributes attributes) {
        super(Type.FIRST_HS);
        this.attributes = attributes;
        this.magicNumber = magicNumber;
    }

    @Override
    public String toString() {
        return "FirstHandshakeMessage{" +
                "attributes=" + attributes +
                ", magicNumber=" + magicNumber +
                '}';
    }
}
