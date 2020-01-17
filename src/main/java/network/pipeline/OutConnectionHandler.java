package network.pipeline;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GenericFutureListener;
import network.ISerializer;
import network.data.Attributes;
import network.data.Host;
import network.listeners.MessageListener;
import network.listeners.OutConnListener;
import network.messaging.NetworkMessage;
import network.userevents.HandshakeCompleted;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class OutConnectionHandler<T> extends ConnectionHandler<T> implements GenericFutureListener<ChannelFuture> {

    private static final Logger logger = LogManager.getLogger(OutConnectionHandler.class);

    // Only change in event loop!
    private State state;

    private final Bootstrap clientBootstrap;
    private final OutConnListener<T> listener;

    enum State {CONNECTING, HANDSHAKING, CONNECTED, DEAD}

    public OutConnectionHandler(Host peer, Bootstrap bootstrap, OutConnListener<T> listener,
                                MessageListener<T> consumer, ISerializer<T> serializer,
                                EventLoop loop, Attributes attrs, int hbInterval, int hbTolerance) {
        super(consumer, loop,false);
        this.peer = peer;
        this.attributes = attrs;

        this.listener = listener;

        this.state = State.CONNECTING;
        this.channel = null;
        this.clientBootstrap = bootstrap.clone();
        this.clientBootstrap.remoteAddress(peer.getAddress(), peer.getPort());
        this.clientBootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast("IdleStateHandler", new IdleStateHandler(hbTolerance,
                        hbInterval, 0, MILLISECONDS));
                ch.pipeline().addLast("MessageDecoder", new MessageDecoder<>(serializer));
                ch.pipeline().addLast("MessageEncoder", new MessageEncoder<>(serializer));
                ch.pipeline().addLast("OutHandshakeHandler", new OutHandshakeHandler(attributes));
                ch.pipeline().addLast("OutCon", OutConnectionHandler.this);
            }
        });
        this.clientBootstrap.group(loop);

        connect();
    }

    //Concurrent - Adds event to loop
    private void connect() {
        loop.execute(() -> {
            if (channel != null && channel.isOpen())
                throw new AssertionError("Channel open in connect: " + peer);
            logger.debug("Connecting to " + peer);
            channel = clientBootstrap.connect().addListener(this).channel();
        });
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (state != State.CONNECTING || ctx.channel() != channel)
            throw new AssertionError("Channel active without being in disconnected state: " + peer);
        state = State.HANDSHAKING;
    }

    //Concurrent - Adds event to loop
    public void sendMessage(T msg) {
        loop.execute(() -> {
            if (state == State.CONNECTED) {
                logger.debug("Writing " + msg + " to outChannel of " + peer);
                channel.writeAndFlush(new NetworkMessage(NetworkMessage.APP_MSG, msg));
            } else
                logger.error("Writing message " + msg + " to channel " + peer + " in unprepared state " + state);
        });
    }

    //Concurrent - Adds event to loop
    public void disconnect() {
        loop.execute(() -> {
            if (state == State.DEAD)
                return;
            logger.debug("Disconnecting channel to: " + peer + ", status was " + state);
            channel.flush();
            channel.close();
        });
    }

    @Override
    public void internalUserEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof HandshakeCompleted) {
            if (state != State.HANDSHAKING || ctx.channel() != channel)
                throw new AssertionError("Handshake completed while not in handshake state: " + peer);
            state = State.CONNECTED;
            logger.debug("Handshake completed to: " + peer);
            listener.outboundConnectionUp(this);
        } else
            logger.warn("Unknown user event caught: " + evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (state == State.DEAD) return;
        logger.debug("Out connection exception: " + peer + " " + cause);
        switch (state) {
            case CONNECTED:
                listener.outboundConnectionDown(this, cause);
                break;
            case HANDSHAKING:
                listener.outboundConnectionFailed(this, cause);
                break;
            default:
                throw new AssertionError("State is " + state + " in exception caught closed callback");
        }
        state = State.DEAD;
        if(ctx.channel().isOpen())
            ctx.close();
    }

    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
        //Connection callback
        if (!future.isSuccess()) {
            logger.debug("Connecting failed: " + future.cause());
            if (state != State.CONNECTING)
                throw new AssertionError("State is " + state + " in connecting callback");
            listener.outboundConnectionFailed(this, future.cause());
            state = State.DEAD;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (state == State.DEAD) return;
        //Callback after connection established
        logger.debug("Connection closed: " + peer);
        switch (state) {
            case CONNECTED:
                listener.outboundConnectionDown(this, null);
                break;
            case HANDSHAKING:
                listener.outboundConnectionFailed(this, null);
                break;
            default:
                throw new AssertionError("State is " + state + " in connection closed callback");
        }
        state = State.DEAD;
    }

    @Override
    public String toString() {
        return "InConnection{" +
                "peer=" + peer +
                ", attributes=" + attributes +
                ", channel=" + channel +
                '}';
    }
}