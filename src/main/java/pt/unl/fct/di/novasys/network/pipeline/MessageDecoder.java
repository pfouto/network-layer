package pt.unl.fct.di.novasys.network.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.messaging.NetworkMessage;
import pt.unl.fct.di.novasys.network.messaging.control.ControlMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

public class MessageDecoder<T> extends ByteToMessageDecoder {

    private static final Logger logger = LogManager.getLogger(MessageDecoder.class);

    private final ISerializer<T> serializer;

    private long receivedAppBytes;
    private long receivedControlBytes;
    private long receivedAppMessages;
    private long receivedControlMessages;

    public MessageDecoder(ISerializer<T> serializer) {
        this.serializer = serializer;
        this.receivedAppBytes = 0;
        this.receivedAppMessages = 0;
        this.receivedControlBytes = 0;
        this.receivedControlMessages = 0;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws IOException {
        if (in.readableBytes() < Integer.BYTES)  return;

        int msgSize = in.getInt(in.readerIndex());
        if (in.readableBytes() < msgSize + Integer.BYTES)
            return;

        in.skipBytes(4);
        byte code = in.readByte();
        Object payload;
        switch (code){
            case NetworkMessage.CTRL_MSG:
                payload = ControlMessage.serializer.deserialize(in);
                receivedControlMessages++;
                receivedControlBytes += 4 + msgSize;
                break;
            case NetworkMessage.APP_MSG:
                payload = serializer.deserialize(in);
                receivedAppBytes += 4 + msgSize;
                receivedAppMessages++;
                break;
            default:
                throw new AssertionError("Unknown msg code in decoder: " + code);
        }
        out.add(new NetworkMessage(code, payload));
    }

    public long getReceivedAppBytes() {
        return receivedAppBytes;
    }

    public long getReceivedAppMessages() {
        return receivedAppMessages;
    }

    public long getReceivedControlBytes() {
        return receivedControlBytes;
    }

    public long getReceivedControlMessages() {
        return receivedControlMessages;
    }
}
