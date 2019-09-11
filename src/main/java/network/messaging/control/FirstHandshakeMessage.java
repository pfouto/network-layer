package network.messaging.control;

import io.netty.buffer.ByteBuf;
import network.Host;

import java.net.UnknownHostException;

public class FirstHandshakeMessage extends ControlMessage {

    public final Host clientHost;

    public FirstHandshakeMessage(Host clientHost) {
        super(Type.FIRST_HS);
        this.clientHost = clientHost;
    }

    @Override
    public String toString() {
        return type + " | " + clientHost;
    }

    @SuppressWarnings("Duplicates")
    public static ControlMessageSerializer serializer = new ControlMessageSerializer<FirstHandshakeMessage>() {
        public void serialize(FirstHandshakeMessage msg, ByteBuf out) {
            msg.clientHost.serialize(out);
        }

        public FirstHandshakeMessage deserialize(ByteBuf in) throws UnknownHostException {
            return new FirstHandshakeMessage(Host.deserialize(in));
        }

        public int serializedSize(FirstHandshakeMessage msg) {
            return msg.clientHost.serializedSize();
        }
    };
}
