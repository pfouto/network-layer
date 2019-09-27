package network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.GenericFutureListener;
import network.messaging.NetworkMessage;
import network.pipeline.MessageDecoder;
import network.pipeline.MessageEncoder;
import network.pipeline.OutExceptionHandler;
import network.pipeline.OutHandshakeHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PeerOutConnection extends ChannelInitializer<SocketChannel> implements GenericFutureListener<ChannelFuture> {

    private static final Logger logger = LogManager.getLogger(PeerOutConnection.class);

    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

    private AtomicReference<Status> status;
    private Channel channel;
    private Host peerHost;
    private Host myHost;
    private Bootstrap clientBootstrap;
    private Timer reconnectTimer;
    private final Queue<NetworkMessage> backlog;
    private int reconnectAttempts;

    private Set<INodeListener> references;
    private Map<Channel, NetworkMessage> transientChannels;

    private Map<Short, ISerializer> serializers;
    private Set<INodeListener> nodeListeners;

    private NetworkConfiguration config;

    enum Status {DISCONNECTED, ACTIVE, HANDSHAKING, RETYING}

    private boolean outsideNodeUp;

    //TODO REFACTOR EVERYTHING

    PeerOutConnection(Host peerHost, Host myHost, Bootstrap bootstrap, Set<INodeListener> nodeListeners,
                      Map<Short, ISerializer> serializers, NetworkConfiguration config) {
        this.peerHost = peerHost;
        this.myHost = myHost;
        this.nodeListeners = nodeListeners;
        this.serializers = serializers;
        this.config = config;

        this.status = new AtomicReference<>(Status.DISCONNECTED);
        this.channel = null;
        this.transientChannels = new ConcurrentHashMap<>();
        this.reconnectAttempts = 0;
        this.clientBootstrap = bootstrap.clone();
        this.clientBootstrap.remoteAddress(peerHost.getAddress(), peerHost.getPort());
        this.clientBootstrap.handler(this);
        this.outsideNodeUp = false;

        this.backlog = new ConcurrentLinkedQueue<>();

        this.references = new HashSet<>();

        this.reconnectTimer = new Timer();
    }

    void addReference(INodeListener ref) {
        rwl.writeLock().lock();
        references.add(ref);
        if (status.compareAndSet(Status.DISCONNECTED, Status.RETYING))
            reconnect();
        rwl.writeLock().unlock();
    }

    void removeReference(INodeListener ref) {
        rwl.writeLock().lock();
        references.remove(ref);
        if(references.size() == 0){
            status.set(Status.DISCONNECTED);
            reconnectTimer.cancel();
            channel.close();
        }
        rwl.writeLock().unlock();
    }

    void sendMessageTransientChannel(NetworkMessage msg) {
        Channel transientChannel = clientBootstrap.attr(NetworkService.TRANSIENT_KEY, true).connect().channel();
        transientChannels.put(transientChannel, msg);
        transientChannel.closeFuture().addListener(this);
    }

    //Concurrent
    void sendMessage(NetworkMessage msg) {
        //TODO add to eventloop
        rwl.readLock().lock();
        Status s = status.get();
        if (s == Status.DISCONNECTED)
            logger.error("Sending message to closed connection. Forgot to use addPeer? " + msg + " " + peerHost);
        else if (s == Status.ACTIVE)
            channel.writeAndFlush(msg);
            //TODO return channelFuture?
        else if (s == Status.HANDSHAKING || s == Status.RETYING)
            backlog.add(msg);
        rwl.readLock().unlock();
    }

    private void writeBacklog() {
        int count = 0;
        while (channel.isActive()) {
            NetworkMessage msg = backlog.poll();
            if (msg == null) break;
            channel.writeAndFlush(msg);
            count++;
        }
        if (count > 0)
            channel.flush();
    }

    private synchronized void reconnect() {
        //TODO schedule event loop
        logger.info("reconnect");
        reconnectAttempts++;
        if (channel != null && channel.isOpen())
            throw new AssertionError("Channel open in reconnect: " + peerHost);
        channel = clientBootstrap.attr(NetworkService.TRANSIENT_KEY, false).connect().channel();
        channel.closeFuture().addListener(this);
    }

    public void channelActiveCallback(Channel c) {
        if (c.attr(NetworkService.TRANSIENT_KEY).get() != null)
            return;
        if (status.get() != Status.DISCONNECTED || c != channel)
            throw new AssertionError("Channel active without being in disconnected state: " + peerHost);
        status.set(Status.HANDSHAKING);
    }

    public void handshakeCompletedCallback(Channel c) {

        //TODO is this inside event loop?

        NetworkMessage networkMessage = transientChannels.remove(c);
        if (networkMessage != null) {
            c.writeAndFlush(networkMessage);
            return;
        }

        rwl.writeLock().lock();

        if (status.get() != Status.HANDSHAKING || c != channel)
            throw new AssertionError("Handshake completed without being in handshake state: " + peerHost);

        logger.debug("Handshake completed to: " + c.remoteAddress());
        writeBacklog();
        status.set(Status.ACTIVE);

        if (!outsideNodeUp) {
            outsideNodeUp = true;
            nodeListeners.forEach(l -> l.nodeUp(peerHost));
        } else {
            logger.warn("Node connection reestablished: " + peerHost);
            nodeListeners.forEach(l -> l.nodeConnectionReestablished(peerHost));
        }
        reconnectAttempts = 0;

        rwl.writeLock().unlock();
    }

    //Channel Close
    @Override
    public void operationComplete(ChannelFuture future) {

        //TODO is this inside event loop?

        if (future.channel().attr(NetworkService.TRANSIENT_KEY).get())
            return;
        if (future != channel.closeFuture())
            throw new AssertionError("Future called for not current channel: " + peerHost);
        if (status.get() == Status.DISCONNECTED) return;

        if (reconnectAttempts == config.RECONNECT_ATTEMPTS_BEFORE_DOWN && outsideNodeUp) {
            nodeListeners.forEach(n -> n.nodeDown(peerHost));
            outsideNodeUp = false;
        }

        Status andSet = status.getAndSet(Status.RETYING);
        if (andSet == Status.DISCONNECTED) {
            status.set(Status.DISCONNECTED);
            return;
        }

        //TODO change this to eventloop schedule
        reconnectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (status.get() == Status.RETYING) reconnect();
                else throw new AssertionError("Channel not disconnected in babel.timer: " + peerHost);
            }
        }, reconnectAttempts > config.RECONNECT_ATTEMPTS_BEFORE_DOWN ?
                                        config.RECONNECT_INTERVAL_AFTER_DOWN_MILLIS :
                                        config.RECONNECT_INTERVAL_BEFORE_DOWN_MILLIS);
    }

    Status getStatus() {
        return status.get();
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast("ReadTimeoutHandler",
                              new ReadTimeoutHandler(config.IDLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        ch.pipeline().addLast("MessageDecoder", new MessageDecoder(serializers));
        ch.pipeline().addLast("MessageEncoder", new MessageEncoder(serializers));
        ch.pipeline().addLast("OutHandshakeHandler", new OutHandshakeHandler(myHost, this));
        ch.pipeline().addLast("OutEventExceptionHandler", new OutExceptionHandler());
    }

}