package network;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
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

    static final Class<? extends Channel> channelClass;
    static final Class<? extends ServerChannel> serverChannelClass;

    static {
        if (Epoll.isAvailable()) {
            channelClass = EpollSocketChannel.class;
            serverChannelClass = EpollServerSocketChannel.class;
        } else if (KQueue.isAvailable()) {
            channelClass = KQueueSocketChannel.class;
            serverChannelClass = KQueueServerSocketChannel.class;
        } else {
            channelClass = NioSocketChannel.class;
            serverChannelClass = NioServerSocketChannel.class;
        }
    }

    public NetworkManager(ISerializer<T> serializer, MessageListener<T> consumer,
                          int hbInterval, int hbTolerance, int connectTimeout) {
        //Default number of threads for worker groups is (from netty) number of core * 2
        this(serializer, consumer, hbInterval, hbTolerance, connectTimeout, 0);
    }

    public NetworkManager(ISerializer<T> serializer, MessageListener<T> consumer,
                          int hbInterval, int hbTolerance, int connectTimeout, int nWorkerThreads) {
        this(serializer, consumer, hbInterval, hbTolerance, connectTimeout, createNewWorkerGroup(nWorkerThreads));
    }

    public NetworkManager(ISerializer<T> serializer, MessageListener<T> consumer,
                          int hbInterval, int hbTolerance, int connectTimeout, EventLoopGroup workerGroup) {
        this.serializer = serializer;
        this.consumer = consumer;
        this.hbInterval = hbInterval;
        this.hbTolerance = hbTolerance;
        this.workerGroup = workerGroup;

        this.clientBootstrap = new Bootstrap()
                .channel(channelClass)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
    }

    public Connection<T> createConnection(Host peer, Attributes attrs, OutConnListener<T> listener) {
        return new OutConnectionHandler<>(peer, clientBootstrap, listener, consumer,
                serializer, workerGroup.next(), attrs, hbInterval, hbTolerance);
    }

    public void createServerSocket(InConnListener<T> listener, Host listenAddr, AttributeValidator validator,
                                   int childThreads) {
        createServerSocket(listener, listenAddr, validator, childThreads, 1);
    }

    public void createServerSocket(InConnListener<T> listener, Host listenAddr, AttributeValidator validator) {
        createServerSocket(listener, listenAddr, validator, 0, 1);
    }

    public void createServerSocket(InConnListener<T> listener, Host listenAddr, AttributeValidator validator,
                                   int childThreads, int parentThreads) {
        createServerSocket(listener, listenAddr, validator, createNewWorkerGroup(childThreads),
                createNewWorkerGroup(parentThreads));
    }

    public void createServerSocket(InConnListener<T> listener, Host listenAddr, AttributeValidator validator,
                                   EventLoopGroup childGroup) {
        createServerSocket(listener, listenAddr, validator, childGroup, createNewWorkerGroup(1));
    }

    public void createServerSocket(InConnListener<T> listener, Host listenAddr, AttributeValidator validator,
                                   EventLoopGroup childGroup, EventLoopGroup parentGroup) {
        //Default number of threads for boss group is 1
        if (serverChannel != null) return;

        //TODO change groups options
        ServerBootstrap b = new ServerBootstrap();
        b.group(parentGroup, childGroup).channel(serverChannelClass);

        b.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast("IdleHandler", new IdleStateHandler(hbTolerance, hbInterval, 0, MILLISECONDS));
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
            serverChannel = b.bind(listenAddr.getAddress(), listenAddr.getPort()).addListener(
                    cf -> listener.serverSocketBind(cf.isSuccess(), cf.cause())).channel();

            serverChannel.closeFuture().addListener(cf -> listener.serverSocketClose(cf.isSuccess(), cf.cause()));
        } catch (Exception e) {
            listener.serverSocketBind(false, e);
        }
    }

    public static EventLoopGroup createNewWorkerGroup(int nThreads) {
        if (Epoll.isAvailable()) return new EpollEventLoopGroup(nThreads);
        else if (KQueue.isAvailable()) return new KQueueEventLoopGroup(nThreads);
        else return new NioEventLoopGroup(nThreads);
    }
}
