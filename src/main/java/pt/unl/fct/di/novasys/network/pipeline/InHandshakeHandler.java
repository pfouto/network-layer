package pt.unl.fct.di.novasys.network.pipeline;

import io.netty.channel.*;
import pt.unl.fct.di.novasys.network.AttributeValidator;
import pt.unl.fct.di.novasys.network.data.Attributes;
import pt.unl.fct.di.novasys.network.messaging.NetworkMessage;
import pt.unl.fct.di.novasys.network.messaging.control.ControlMessage;
import pt.unl.fct.di.novasys.network.messaging.control.FirstHandshakeMessage;
import pt.unl.fct.di.novasys.network.messaging.control.InvalidAttributesMessage;
import pt.unl.fct.di.novasys.network.messaging.control.SecondHandshakeMessage;
import pt.unl.fct.di.novasys.network.userevents.HandshakeCompleted;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InHandshakeHandler extends ChannelDuplexHandler {

    private static final Logger logger = LogManager.getLogger(InHandshakeHandler.class);

    private final AttributeValidator validator;
    private final Attributes myAttrs;

    public InHandshakeHandler(AttributeValidator validator, Attributes myAttrs) {
        this.validator = validator;
        this.myAttrs = myAttrs;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
        NetworkMessage msg = (NetworkMessage) obj;
        if (msg.code != NetworkMessage.CTRL_MSG)
            throw new Exception("Received application message in inHandshake: " + msg);

        ControlMessage cMsg = (ControlMessage) msg.payload;
        if(cMsg.type == ControlMessage.Type.HEARTBEAT) return;

        if (cMsg.type == ControlMessage.Type.FIRST_HS) {
            FirstHandshakeMessage fhm = (FirstHandshakeMessage) cMsg;
            if (validator.validateAttributes(fhm.attributes)) {
                ctx.channel().writeAndFlush(new NetworkMessage(NetworkMessage.CTRL_MSG, new SecondHandshakeMessage(myAttrs)));
                ctx.fireUserEventTriggered(new HandshakeCompleted(fhm.attributes));
                ctx.pipeline().remove(this);
            } else {
                ctx.channel().writeAndFlush(new NetworkMessage(NetworkMessage.CTRL_MSG, new InvalidAttributesMessage()));
                throw new Exception("Invalid attributes received");
            }
        } else {
            throw new Exception("Received unexpected control message in inHandshake: " + msg);
        }
    }
}
