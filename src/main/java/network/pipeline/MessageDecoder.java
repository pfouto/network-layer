package network.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import network.ISerializer;
import network.messaging.NetworkMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public class MessageDecoder extends ByteToMessageDecoder
{

    private static final Logger logger = LogManager.getLogger(MessageDecoder.class);

    private Map<Short, ISerializer> serializers;

    public MessageDecoder(Map<Short, ISerializer> serializers)
    {
        this.serializers = serializers;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws UnknownHostException
    {
        if (in.readableBytes() < 4)
            return;

        int msgSize = in.getInt(in.readerIndex());
        if (in.readableBytes() < msgSize + 4) {
            return;
        }
        in.skipBytes(4);

        short code = in.readShort();
        Object payload = serializers.get(code).deserialize(in);
        NetworkMessage networkMessage = new NetworkMessage(code, payload);
        out.add(networkMessage);
    }

}
