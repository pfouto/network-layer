package network.pipeline;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OutExceptionHandler extends ChannelDuplexHandler {

    private static final Logger logger = LogManager.getLogger(OutExceptionHandler.class);

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                Throwable cause = future.cause();
                logger.error(
                        "Outbound connection exception: " + ctx.channel().remoteAddress().toString() + " " + cause);
                if (cause.getCause() instanceof AssertionError || cause.getCause() instanceof NullPointerException) {
                    cause.printStackTrace();
                    System.exit(1);
                }
                ctx.close();
            }
        }));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        logger.warn("Received unexpected userEvent: " + evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Outbound connection exception: " + ctx.channel().remoteAddress().toString() + " " + cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.debug("Out to " + ctx.channel().remoteAddress() + " closed");
        ctx.fireChannelInactive();
    }
}
