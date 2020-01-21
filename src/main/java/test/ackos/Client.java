package test.ackos;

import channel.ChannelEvent;
import channel.ChannelListener;
import channel.IChannel;
import channel.ackos.AckosChannel;
import channel.ackos.events.NodeDownEvent;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import test.ByeMsg;
import test.FTPMessage;
import test.HelloMsg;
import test.PartMsg;

import java.io.*;
import java.net.InetAddress;
import java.util.Arrays;
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

    @Override
    public void messageSent(FTPMessage msg, Host to) {
        logger.info("Ack: " + msg);
        if (msg instanceof ByeMsg) {
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

    @Override
    public void messageFailed(FTPMessage msg, Host to, Throwable cause) {

    }

    private void onNodeDown(NodeDownEvent<FTPMessage> evt) {
        logger.error(evt);
    }

    @Override
    public void deliverEvent(ChannelEvent evt) {
        if (evt instanceof NodeDownEvent)
            onNodeDown((NodeDownEvent<FTPMessage>) evt);
    }

    public static void main(String[] args) throws Exception {
        new Client(args);
    }
}
