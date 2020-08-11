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

    private final ISerializer<T> serializer;

    private int sentAppBytes;
    private int sentControlBytes;
    private int sentAppMessages;
    private int sentControlMessages;

    public MessageEncoder(ISerializer<T> serializer) {
        this.serializer = serializer;
        this.sentAppBytes = 0;
        this.sentAppMessages = 0;
        this.sentControlBytes = 0;
        this.sentControlMessages = 0;
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
                sentControlMessages++;
                sentControlBytes += (out.writerIndex() - sizeIndex);
                break;
            case NetworkMessage.APP_MSG:
                serializer.serialize((T) msg.payload, out);
                sentAppMessages++;
                sentAppBytes += (out.writerIndex() - sizeIndex);
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

    public int getSentAppBytes() {
        return sentAppBytes;
    }

    public int getSentAppMessages() {
        return sentAppMessages;
    }

    public int getSentControlBytes() {
        return sentControlBytes;
    }

    public int getSentControlMessages() {
        return sentControlMessages;
    }
}
