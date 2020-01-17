package test;

import io.netty.buffer.ByteBuf;
import network.ISerializer;

import java.io.IOException;

public class IntMessage {

    public int value;

    public IntMessage(int value){
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "IntMessage{" +
                "value=" + value +
                '}';
    }

    public static ISerializer<IntMessage> serializer = new ISerializer<IntMessage>() {
        @Override
        public void serialize(IntMessage intMessage, ByteBuf out) throws IOException {
            out.writeInt(intMessage.value);
        }

        @Override
        public IntMessage deserialize(ByteBuf in) throws IOException {
            return new IntMessage(in.readInt());
        }
    };
}
