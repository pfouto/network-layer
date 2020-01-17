package test;

import channel.ChannelEvent;
import channel.ChannelListener;
import channel.IChannel;
import channel.ackos.AckosChannel;
import channel.ackos.events.AckosEvent;
import channel.ackos.events.MessageAckEvent;
import channel.ackos.events.NodeDownEvent;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

public class Client implements ChannelListener<FTPMessage> {

    static {
        System.setProperty("log4j.configurationFile", "log4j2.xml");
    }

    private static final Logger logger = LogManager.getLogger(Client.class);

    IChannel<FTPMessage> channel;
    private Host server;
    private FileInputStream fin = null;
    private long total = 0;

    public Client(String[] args) throws Exception {
        channel = new AckosChannel<>(FTPMessage.serializer, this, new Properties());
        server = new Host(InetAddress.getByName("localhost"), AckosChannel.DEFAULT_PORT);
        startTransfer(args[0]);
    }

    public void startTransfer(String path) throws FileNotFoundException {
        File file = new File(path);
        fin = new FileInputStream(file);
        channel.sendMessage(new HelloMsg(path.substring(path.lastIndexOf("/") + 1)), server);
    }

    @Override
    public void deliverMessage(FTPMessage msg, Host from) {
        logger.info("Message: " + msg + " : " + from);
    }

    private void onMessageAck(MessageAckEvent<FTPMessage> evt) {
        logger.info("Ack: " + evt);
        if (evt.getMessage() instanceof ByeMsg) {
            logger.info("Done!");
            return;
        }
        byte[] nextBytes = new byte[512];
        try {
            int read = fin.read(nextBytes);
            if (read > 0) {
                total += read;
                channel.sendMessage(new PartMsg(Arrays.copyOf(nextBytes, read)), server);
            } else {
                channel.sendMessage(new ByeMsg(total), server);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void onNodeDown(NodeDownEvent<FTPMessage> evt) {
        logger.error(evt);
    }

    @Override
    public void deliverEvent(ChannelEvent<FTPMessage> evt) {
        if (evt instanceof MessageAckEvent) {
            onMessageAck((MessageAckEvent<FTPMessage>) evt);
        } else {
            onNodeDown((NodeDownEvent<FTPMessage>) evt);
        }
    }

    public static void main(String[] args) throws Exception {
        new Client(args);
    }
}
