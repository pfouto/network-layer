package network.pipeline;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import network.Host;
import network.IMessageConsumer;
import network.messaging.NetworkMessage;
import network.messaging.control.ControlMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class InConnectionHandler extends ChannelDuplexHandler {
    private static final Logger logger = LogManager.getLogger(InConnectionHandler.class);

    private Map<Short, IMessageConsumer> messageConsumers;
    private Host peerHost;

    InConnectionHandler(Host peerHost, Map<Short, IMessageConsumer> messageConsumers) {
        this.peerHost = peerHost;
        this.messageConsumers = messageConsumers;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        assert ((ControlMessage) (((NetworkMessage) msg).payload)).type == ControlMessage.Type.HEARTBEAT;
        ctx.write(msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        NetworkMessage netMsg = (NetworkMessage) msg;
        if (netMsg.code == ControlMessage.MSG_CODE)
            throw new AssertionError("Control message received in InConnectionHandler, should not happen: " + msg);

        IMessageConsumer iMessageConsumer = messageConsumers.get(netMsg.code);
        if (iMessageConsumer == null)
            throw new AssertionError("No consumer for received message: " + msg);
        iMessageConsumer.deliverMessage(netMsg.code, netMsg.payload, peerHost);
    }
}