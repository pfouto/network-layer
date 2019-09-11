package network.pipeline;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import network.IMessageConsumer;
import network.messaging.NetworkMessage;
import network.messaging.control.ControlMessage;
import network.messaging.control.FirstHandshakeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class InHandshakeHandler extends ChannelDuplexHandler {

    private static final Logger logger = LogManager.getLogger(InHandshakeHandler.class);

    private Map<Byte, IMessageConsumer> messageConsumers;

    public InHandshakeHandler(Map<Byte, IMessageConsumer> messageConsumers) {
        this.messageConsumers = messageConsumers;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        NetworkMessage netMsg = (NetworkMessage) msg;
        ControlMessage payload = (ControlMessage) netMsg.payload;
        if (payload.type == ControlMessage.Type.FIRST_HS) {
            ctx.pipeline().replace(this, "InConnectionHandler",
                                   new InConnectionHandler(((FirstHandshakeMessage) payload).clientHost,
                                                           messageConsumers));
        } else {
            throw new AssertionError("Received unexpected message in handshake: " + msg);
        }
    }
}
