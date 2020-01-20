package network;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import network.data.Attributes;
import network.data.Host;
import network.listeners.MessageListener;
import network.listeners.InConnListener;
import network.listeners.OutConnListener;
import network.pipeline.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class NetworkManager<T> {

    private static final Logger logger = LogManager.getLogger(NetworkManager.class);

    private Bootstrap clientBootstrap;
    private EventLoopGroup workerGroup;

    private Channel serverChannel;

    private final ISerializer<T> serializer;
    private final MessageListener<T> consumer;
    private final int hbInterval;
    private final int hbTolerance;
    private final int connectTimeout;

    public NetworkManager(ISerializer<T> serializer, MessageListener<T> consumer,
                          int hbInterval, int hbTolerance, int connectTimeout) {
        clientBootstrap = setupClientBootstrap();
        this.serializer = serializer;
        this.consumer = consumer;
        this.hbInterval = hbInterval;
        this.hbTolerance = hbTolerance;
        this.connectTimeout = connectTimeout;
    }

    public Connection<T> createConnection(Host peer, Attributes attrs, OutConnListener<T> listener) {
        return new OutConnectionHandler<>(peer, clientBootstrap, listener, consumer,
                serializer, workerGroup.next(), attrs, hbInterval, hbTolerance);
    }

    public void createServerSocket(InConnListener<T> listener, Host listenIPAndPort, AttributeValidator validator) {
        if (serverChannel != null) return;

        //TODO change groups options
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(workerGroup).channel(NioServerSocketChannel.class);
        b.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast("IdleStateHandler",
                        new IdleStateHandler(hbTolerance, hbInterval, 0, MILLISECONDS));
                ch.pipeline().addLast("MessageDecoder", new MessageDecoder<>(serializer));
                ch.pipeline().addLast("MessageEncoder", new MessageEncoder<>(serializer));
                ch.pipeline().addLast("InHandshakeHandler", new InHandshakeHandler(validator));
                ch.pipeline().addLast("InCon", new InConnectionHandler<>(listener, consumer, ch.eventLoop()));
            }
        });
        //TODO: study options / child options
        b.option(ChannelOption.SO_BACKLOG, 128);
        b.childOption(ChannelOption.SO_KEEPALIVE, true);
        b.childOption(ChannelOption.TCP_NODELAY, true);

        try {
            serverChannel = b.bind(listenIPAndPort.getAddress(), listenIPAndPort.getPort()).addListener(cf -> {
                listener.serverSocketBind(cf.isSuccess(), cf.cause());
            }).channel();

            serverChannel.closeFuture().addListener(cf -> {
                listener.serverSocketClose(cf.isSuccess(), cf.cause());
            });
        } catch (Exception e) {
            listener.serverSocketBind(false, e);
        }
    }

    private Bootstrap setupClientBootstrap() {
        //TODO support custom groups
        workerGroup = new NioEventLoopGroup();
        Bootstrap newClientBootstrap = new Bootstrap();
        newClientBootstrap.channel(NioSocketChannel.class);
        //TODO maybe change options
        newClientBootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        newClientBootstrap.option(ChannelOption.TCP_NODELAY, true);
        newClientBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
        return newClientBootstrap;
    }
}
