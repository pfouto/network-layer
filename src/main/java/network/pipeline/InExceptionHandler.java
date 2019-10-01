package network.pipeline;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import network.messaging.NetworkMessage;
import network.messaging.control.ControlMessage;
import network.messaging.control.HeartbeatMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InExceptionHandler extends ChannelDuplexHandler {

    private static final Logger logger = LogManager.getLogger(InExceptionHandler.class);

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                Throwable cause = future.cause();
                logger.error("Inbound connection exception: " + ctx.channel().remoteAddress().toString() + " " + cause);
                if (cause.getCause() instanceof AssertionError) {
                    cause.printStackTrace();
                    System.exit(1);
                }
                ctx.close();
            }
        }));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.WRITER_IDLE) {
                ctx.channel().writeAndFlush(new NetworkMessage(ControlMessage.MSG_CODE, new HeartbeatMessage()));
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Inbound connection exception: " + ctx.channel().remoteAddress().toString() + " " + cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        //logger.debug("In from " + ctx.channel().remoteAddress() + " closed");
        ctx.fireChannelInactive();
    }
}
