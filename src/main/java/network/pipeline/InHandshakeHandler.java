package network.pipeline;

import io.netty.channel.*;
import network.AttributeValidator;
import network.messaging.NetworkMessage;
import network.messaging.control.ControlMessage;
import network.messaging.control.FirstHandshakeMessage;
import network.messaging.control.InvalidAttributesMessage;
import network.messaging.control.SecondHandshakeMessage;
import network.userevents.HandshakeCompleted;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InHandshakeHandler extends ChannelDuplexHandler {

    private static final Logger logger = LogManager.getLogger(InHandshakeHandler.class);

    private AttributeValidator validator;

    public InHandshakeHandler(AttributeValidator validator) {
        this.validator = validator;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
        NetworkMessage msg = (NetworkMessage) obj;
        if (msg.code != NetworkMessage.CTRL_MSG)
            throw new Exception("Received unexpected message in handshake: " + msg);

        ControlMessage cMsg = (ControlMessage) msg.payload;
        if (cMsg.type != ControlMessage.Type.FIRST_HS)
            throw new Exception("Received unexpected control message in handshake: " + msg);

        FirstHandshakeMessage fhm = (FirstHandshakeMessage) cMsg;
        if(validator.validateAttributes(fhm.attributes)){
            ctx.channel().writeAndFlush(new NetworkMessage(NetworkMessage.CTRL_MSG, new SecondHandshakeMessage()));
            ctx.fireUserEventTriggered(new HandshakeCompleted(fhm.attributes));
            ctx.pipeline().remove(this);
        } else {
            ctx.channel().writeAndFlush(new NetworkMessage(NetworkMessage.CTRL_MSG, new InvalidAttributesMessage()));
            throw new Exception("Invalid attributes received");
        }
    }
}
