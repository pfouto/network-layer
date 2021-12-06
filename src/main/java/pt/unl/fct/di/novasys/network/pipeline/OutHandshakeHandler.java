package pt.unl.fct.di.novasys.network.pipeline;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import pt.unl.fct.di.novasys.network.data.Attributes;
import pt.unl.fct.di.novasys.network.messaging.NetworkMessage;
import pt.unl.fct.di.novasys.network.messaging.control.ControlMessage;
import pt.unl.fct.di.novasys.network.messaging.control.FirstHandshakeMessage;
import pt.unl.fct.di.novasys.network.messaging.control.SecondHandshakeMessage;
import pt.unl.fct.di.novasys.network.userevents.HandshakeCompleted;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OutHandshakeHandler extends ChannelDuplexHandler {

    private static final Logger logger = LogManager.getLogger(OutHandshakeHandler.class);

    private final Attributes attrs;

    public OutHandshakeHandler(Attributes attrs) {
        this.attrs = attrs;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.channel().writeAndFlush(new NetworkMessage(NetworkMessage.CTRL_MSG, new FirstHandshakeMessage(attrs)));
        ctx.fireChannelActive();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {

        NetworkMessage msg = (NetworkMessage) obj;
        if (msg.code != NetworkMessage.CTRL_MSG)
            throw new Exception("Received application message in outHandshake: " + msg);

        ControlMessage cMsg = (ControlMessage) msg.payload;
        if(cMsg.type == ControlMessage.Type.HEARTBEAT) return;

        if(cMsg.type == ControlMessage.Type.SECOND_HS) {
            SecondHandshakeMessage shm = (SecondHandshakeMessage) cMsg;
            ctx.fireUserEventTriggered(new HandshakeCompleted(shm.attributes));
            ctx.pipeline().remove(this);
        } else if (cMsg.type == ControlMessage.Type.INVALID_ATTR){
            throw new Exception("Attributes refused");
        } else {
            throw new Exception("Received unexpected control message in outHandshake: " + msg);
        }
    }
}
