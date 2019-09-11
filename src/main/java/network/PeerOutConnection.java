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
import network.pipeline.OutEventExceptionHandler;
import network.pipeline.OutHandshakeHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PeerOutConnection extends ChannelInitializer<SocketChannel> implements GenericFutureListener<ChannelFuture> {

    private static final Logger logger = LogManager.getLogger(PeerOutConnection.class);

    private AtomicReference<Status> status;
    private Channel channel;
    private Host peerHost;
    private Host myHost;
    private Bootstrap clientBootstrap;
    private Timer reconnectTimer;
    private Set<INodeListener> nodeListeners;
    private final Queue<NetworkMessage> backlog;
    private int reconnectAttempts;

    private Map<Channel, NetworkMessage> transientChannels;

    private Map<Byte, ISerializer> serializers;
    private NetworkConfiguration config;

    enum Status {DISCONNECTED, ACTIVE, HANDSHAKING, TERMINATED}

    private boolean outsideNodeUp;

    PeerOutConnection(Host peerHost, Host myHost, Bootstrap bootstrap, Set<INodeListener> nodeListeners,
                      Map<Byte, ISerializer> serializers, NetworkConfiguration config) {
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

        this.reconnectTimer = new Timer();
        reconnect();
    }

    void sendMessageTransientChannel(NetworkMessage msg) {
        Channel transientChannel = clientBootstrap.attr(NetworkService.TRANSIENT_KEY, true).connect().channel();
        transientChannels.put(transientChannel, msg);
        transientChannel.closeFuture().addListener(this);
    }

    void sendMessage(NetworkMessage msg) {
        if (status.get() == Status.ACTIVE) {
            channel.writeAndFlush(msg);
            //TODO return channelFuture?
        } else if (status.get() == Status.TERMINATED) {
            logger.error("Attempting to send message to terminated channel: " + peerHost);
        } else {
            //Disconnected or handshaking
            logger.debug("Message to backlog..." + msg);
            backlog.add(msg);
            //If status changed to active between "else" and "backlog.add" TODO: is this even possible?
            if (status.get() == Status.ACTIVE)
                writeBacklog();
        }
    }

    private void writeBacklog() {
        int count = 0;
        while (true) {
            if (!channel.isActive()) {
                logger.warn("Channel no longer active in writeBacklog");
                break;
            }
            NetworkMessage msg = backlog.poll();
            if (msg == null) break;
            logger.debug("Writing backlog msg: " + msg + " " + backlog.size());
            channel.writeAndFlush(msg);
            count++;
        }
        if (count > 0)
            channel.flush();
    }

    //TODO maybe call this in "sendMessage" instead of timer...
    private synchronized void reconnect() {
        reconnectAttempts++;
        if (channel != null && channel.isOpen())
            throw new AssertionError("Channel open in reconnect: " + peerHost);
        channel = clientBootstrap.attr(NetworkService.TRANSIENT_KEY, false).connect().channel();
        channel.closeFuture().addListener(this);
    }

    //Channel Close
    @Override
    public void operationComplete(ChannelFuture future) {
        if(future.channel().attr(NetworkService.TRANSIENT_KEY).get())
            return;

        if (future != channel.closeFuture())
            throw new AssertionError("Future called for not current channel: " + peerHost);
        if (status.get() == Status.TERMINATED) return;


        channel = null;
        status.set(Status.DISCONNECTED);

        if (reconnectAttempts == config.RECONNECT_ATTEMPTS_BEFORE_DOWN && outsideNodeUp) {
            nodeListeners.forEach(n -> n.nodeDown(peerHost));
            outsideNodeUp = false;
        }

        Status andSet = status.getAndSet(Status.DISCONNECTED);

        if (andSet == Status.TERMINATED) {
            status.set(Status.TERMINATED);
            return;
        }

        reconnectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (status.get() == Status.DISCONNECTED) reconnect();
                else throw new AssertionError("Channel not disconnected in timer: " + peerHost);
            }
        }, reconnectAttempts > config.RECONNECT_ATTEMPTS_BEFORE_DOWN ?
                                        config.RECONNECT_INTERVAL_AFTER_DOWN_MILLIS :
                                        config.RECONNECT_INTERVAL_BEFORE_DOWN_MILLIS);
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast("ReadTimeoutHandler",
                              new ReadTimeoutHandler(config.IDLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        ch.pipeline().addLast("MessageDecoder", new MessageDecoder(serializers));
        ch.pipeline().addLast("MessageEncoder", new MessageEncoder(serializers));
        ch.pipeline().addLast("OutHandshakeHandler", new OutHandshakeHandler(myHost, this));
        ch.pipeline().addLast("OutEventExceptionHandler", new OutEventExceptionHandler());
    }

    public void handshakeCompletedCallback(Channel c) {

        NetworkMessage networkMessage = transientChannels.get(c);
        if(networkMessage != null){
            c.writeAndFlush(networkMessage);
            return;
        }

        if (status.get() != Status.HANDSHAKING || c != channel)
            throw new AssertionError("Handshake completed without being in handshake state: " + peerHost);

        //TODO should setStatus and writeBacklog be before notifying paxos of nodeUp?
        writeBacklog();
        logger.debug("Handshake completed to: " + c.remoteAddress());
        status.set(Status.ACTIVE);
        writeBacklog();

        if (!outsideNodeUp) {
            outsideNodeUp = true;
            nodeListeners.forEach(l -> l.nodeUp(peerHost));
        } else {
            logger.warn("Node connection reestablished: " + peerHost);
            nodeListeners.forEach(l -> l.nodeConnectionReestablished(peerHost));
        }
        reconnectAttempts = 0;
    }

    public void channelActiveCallback(Channel c) {
        if(c.attr(NetworkService.TRANSIENT_KEY).get())
            return;

        if (status.get() != Status.DISCONNECTED || c != channel)
            throw new AssertionError("Channel active without being in disconnected state: " + peerHost);
        status.set(Status.HANDSHAKING);
    }

    void terminate() {
        status.set(Status.TERMINATED);
        reconnectTimer.cancel();
        if (channel != null) {
            channel.closeFuture().removeListener(this);
            channel.close();
        }

        for (Iterator<Channel> it = transientChannels.keySet().iterator(); it.hasNext(); ) {
            it.next().close();
            it.remove();
        }
    }

    Status getStatus() {
        return status.get();
    }
}