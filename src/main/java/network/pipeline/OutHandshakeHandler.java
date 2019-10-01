package network.pipeline;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import network.Host;
import network.PeerOutConnection;
import network.messaging.NetworkMessage;
import network.messaging.control.ControlMessage;
import network.messaging.control.FirstHandshakeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OutHandshakeHandler extends ChannelDuplexHandler
{
    private static final Logger logger = LogManager.getLogger(OutHandshakeHandler.class);

    private Host myHost;
    private PeerOutConnection peerOutConnection;

    public OutHandshakeHandler(Host myHost, PeerOutConnection peerOutConnection)
    {
        this.myHost = myHost;
        this.peerOutConnection = peerOutConnection;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx)
    {
        peerOutConnection.channelActiveCallback(ctx.channel());

        ChannelFuture channelFuture = ctx.channel().writeAndFlush(new NetworkMessage(ControlMessage.MSG_CODE, new FirstHandshakeMessage(myHost)));
        channelFuture.addListener(listener -> {
            //logger.debug("Handshake completed to " + ctx.channel().remoteAddress().toString());
            ctx.channel().pipeline().replace(this, "OutConnectionHandler", new OutConnectionHandler(ctx));
            peerOutConnection.handshakeCompletedCallback(ctx.channel());
        });
    }
}
