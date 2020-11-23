package pt.unl.fct.di.novasys.network.messaging.control;

import io.netty.buffer.ByteBuf;

public class InvalidAttributesMessage extends ControlMessage {

    public final int magicNumber;

    public InvalidAttributesMessage() {
        this(MAGIC_NUMBER);
    }

    public InvalidAttributesMessage(int magicNumber) {
        super(Type.INVALID_ATTR);
        this.magicNumber = magicNumber;
    }

    @Override
    public String toString() {
        return "InvalidAttributesMessage{" +
                "magicNumber=" + magicNumber +
                '}';
    }

    static ControlMessageSerializer serializer = new ControlMessageSerializer<InvalidAttributesMessage>() {

        public void serialize(InvalidAttributesMessage msg, ByteBuf out) {
            out.writeInt(msg.magicNumber);
        }

        public InvalidAttributesMessage deserialize(ByteBuf in) {
            int magicNumber = in.readInt();
            if(magicNumber != MAGIC_NUMBER)
                throw new RuntimeException("Invalid magic number: " + magicNumber);
            return new InvalidAttributesMessage(magicNumber);
        }
    };
}
