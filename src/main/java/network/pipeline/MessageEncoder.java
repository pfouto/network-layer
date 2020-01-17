package network.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import network.ISerializer;
import network.messaging.NetworkMessage;
import network.messaging.control.ControlMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class MessageEncoder<T> extends MessageToByteEncoder<NetworkMessage> {

    private static final Logger logger = LogManager.getLogger(MessageEncoder.class);

    private ISerializer<T> serializer;

    public MessageEncoder(ISerializer<T> serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, NetworkMessage msg, ByteBuf out) throws IOException {

        int sizeIndex = out.writerIndex();
        out.writeInt(-1);

        int startIndex = out.writerIndex();
        out.writeByte(msg.code);
        switch (msg.code){
            case NetworkMessage.CTRL_MSG:
                ControlMessage.serializer.serialize((ControlMessage) msg.payload,out);
                break;
            case NetworkMessage.APP_MSG:
                serializer.serialize((T) msg.payload, out);
                break;
            default:
                throw new AssertionError("Unknown msg code in encoder: " + msg);
        }

        int serializedSize = out.writerIndex() - startIndex;
        out.markWriterIndex();
        out.writerIndex(sizeIndex);
        out.writeInt(serializedSize);
        out.resetWriterIndex();
    }
}
