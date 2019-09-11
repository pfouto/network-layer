package network.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import network.ISerializer;
import network.messaging.NetworkMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class MessageEncoder extends MessageToByteEncoder<NetworkMessage>
{
    private static final Logger logger = LogManager.getLogger(MessageEncoder.class);

    private Map<Byte, ISerializer> serializers;

    public MessageEncoder(Map<Byte, ISerializer> serializers)
    {
        this.serializers = serializers;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, NetworkMessage msg, ByteBuf out)
    {
        ISerializer iSerializer = serializers.get(msg.code);
        out.writeInt(iSerializer.serializedSize(msg.payload) + 1);
        out.writeByte(msg.code);
        iSerializer.serialize(msg.payload, out);

        assert out.writerIndex() == iSerializer.serializedSize(msg.payload) + 1 + 4;
    }
}
