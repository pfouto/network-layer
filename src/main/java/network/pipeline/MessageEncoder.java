package network.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import network.ISerializer;
import network.messaging.NetworkMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class MessageEncoder extends MessageToByteEncoder<NetworkMessage> {
    private static final Logger logger = LogManager.getLogger(MessageEncoder.class);

    private Map<Short, ISerializer> serializers;

    public MessageEncoder(Map<Short, ISerializer> serializers) {
        this.serializers = serializers;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, NetworkMessage msg, ByteBuf out) {
        int initialIndex = out.writerIndex();

        ISerializer iSerializer = serializers.get(msg.code);
        int serializedSize = iSerializer.serializedSize(msg.payload);
        out.writeInt(serializedSize + 2);
        out.writeShort(msg.code);
        iSerializer.serialize(msg.payload, out);

        int writtenBytes = out.writerIndex() - initialIndex;

        if (writtenBytes != (serializedSize + 2 + 4)) {
            throw new RuntimeException(
                    "Size of message " + msg.getClass() + "( " + msg + " ) is incorrect. Reported value is " + serializedSize + " but internal buffer has " + (writtenBytes - 2 - 4));
        }

        assert out.writerIndex() == iSerializer.serializedSize(msg.payload) + 2 + 4;
    }
}
