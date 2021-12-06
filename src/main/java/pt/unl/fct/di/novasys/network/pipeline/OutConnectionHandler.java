package pt.unl.fct.di.novasys.network.pipeline;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Attributes;
import pt.unl.fct.di.novasys.network.data.Host;
import pt.unl.fct.di.novasys.network.listeners.MessageListener;
import pt.unl.fct.di.novasys.network.listeners.OutConnListener;
import pt.unl.fct.di.novasys.network.messaging.NetworkMessage;
import pt.unl.fct.di.novasys.network.userevents.HandshakeCompleted;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class OutConnectionHandler<T> extends ConnectionHandler<T> implements GenericFutureListener<ChannelFuture> {

    private static final Logger logger = LogManager.getLogger(OutConnectionHandler.class);
    private final Bootstrap clientBootstrap;
    private final OutConnListener<T> listener;
    // Only change in event loop!
    private State state;

    public OutConnectionHandler(Host peer, Bootstrap bootstrap, OutConnListener<T> listener,
                                MessageListener<T> consumer, ISerializer<T> serializer,
                                EventLoop loop, Attributes selfAttrs, int hbInterval, int hbTolerance) {
        super(consumer, loop, false, selfAttrs);
        this.peer = peer;

        this.listener = listener;

        this.state = State.CONNECTING;
        this.channel = null;
        this.clientBootstrap = bootstrap.clone();
        this.clientBootstrap.remoteAddress(peer.getAddress(), peer.getPort());
        this.encoder = new MessageEncoder<>(serializer);
        this.decoder = new MessageDecoder<>(serializer);
        this.clientBootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                if(hbTolerance > 0 || hbInterval > 0)
                    ch.pipeline().addLast("IdleHandler",
                            new IdleStateHandler(hbTolerance, hbInterval, 0, MILLISECONDS));
                ch.pipeline().addLast("MessageDecoder", decoder);
                ch.pipeline().addLast("MessageEncoder", encoder);
                ch.pipeline().addLast("OutHandshakeHandler", new OutHandshakeHandler(selfAttrs));
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
    @Override
    public void sendMessage(T msg, Promise<Void> promise) {
        loop.execute(() -> {
            if (state == State.CONNECTED) {
                logger.debug("Writing " + msg + " to outChannel of " + peer);
                ChannelFuture future = channel.writeAndFlush(new NetworkMessage(NetworkMessage.APP_MSG, msg));
                if (promise != null) future.addListener(new PromiseNotifier<>(promise));
            } else
                logger.warn("Writing message " + msg + " to channel " + peer + " in unprepared state " + state);
        });
    }

    @Override
    public void sendMessage(T msg) {
        sendMessage(msg, null);
    }

    //Concurrent - Adds event to loop
    @Override
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
            this.peerAttributes = ((HandshakeCompleted) evt).getAttr();
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
            case CONNECTING:
                listener.outboundConnectionFailed(this, cause);
                break;
            default:
                throw new AssertionError("State is " + state + " in exception caught closed callback");
        }
        state = State.DEAD;
        if (ctx.channel().isOpen())
            ctx.close();
    }

    @Override
    public void operationComplete(ChannelFuture future) {
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
        return "OutConnectionHandler{" +
                "peer=" + peer +
                ", attributes=" + peerAttributes +
                ", channel=" + channel +
                '}';
    }

    enum State {CONNECTING, HANDSHAKING, CONNECTED, DEAD}
}