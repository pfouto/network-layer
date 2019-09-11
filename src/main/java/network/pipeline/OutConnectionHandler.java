package network.pipeline;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import network.NetworkService;
import network.messaging.NetworkMessage;
import network.messaging.control.ControlMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OutConnectionHandler extends ChannelDuplexHandler {
    private static final Logger logger = LogManager.getLogger(OutConnectionHandler.class);

    private boolean transientChannel;

    OutConnectionHandler(ChannelHandlerContext ctx) {
        transientChannel = ctx.channel().attr(NetworkService.TRANSIENT_KEY).get();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise);
        if (transientChannel)
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        NetworkMessage netMsg = (NetworkMessage) msg;
        if (netMsg.code != ControlMessage.MSG_CODE)
            throw new AssertionError("Not control message received in OutConnectionHandler: " + msg);

        ControlMessage payload = (ControlMessage) netMsg.payload;
        if (payload.type != ControlMessage.Type.HEARTBEAT)
            throw new AssertionError("Unexpected control message received in OutConnectionHandler: " + msg);

        //DO nothing with heartbeats...
    }
}