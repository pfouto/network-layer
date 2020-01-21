package test.clientserver;

import channel.ChannelEvent;
import channel.ChannelListener;
import channel.IChannel;
import channel.ackos.AckosChannel;
import channel.ackos.events.NodeDownEvent;
import channel.simpleclientserver.SimpleClientChannel;
import channel.simpleclientserver.events.ServerDownEvent;
import channel.simpleclientserver.events.ServerFailedEvent;
import channel.simpleclientserver.events.ServerUpEvent;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import test.ByeMsg;
import test.FTPMessage;
import test.HelloMsg;
import test.PartMsg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Properties;

public class Client implements ChannelListener<FTPMessage> {

    static {
        System.setProperty("log4j.configurationFile", "log4j2.xml");
    }

    private static final Logger logger = LogManager.getLogger(Client.class);

    IChannel<FTPMessage> channel;
    private FileInputStream fin = null;
    private long total = 0;
    private String path;

    public Client(String[] args) throws Exception {
        path = args[0];
        Properties properties = new Properties();
        properties.setProperty("address", "localhost");
        channel = new SimpleClientChannel<>(FTPMessage.serializer, this, properties);
    }

    public void startTransfer(String path) throws FileNotFoundException {
        File file = new File(path);
        fin = new FileInputStream(file);
        channel.sendMessage(new HelloMsg(path.substring(path.lastIndexOf("/") + 1)), null);
    }

    @Override
    public void deliverMessage(FTPMessage msg, Host from) {
        logger.info("Message: " + msg + " : " + from);
    }

    @Override
    public void messageSent(FTPMessage msg, Host to) {
        logger.info("Sent: " + msg);
        if (msg instanceof ByeMsg) {
            logger.info("Done!");
            return;
        }
        byte[] nextBytes = new byte[512];
        try {
            int read = fin.read(nextBytes);
            if (read > 0) {
                total += read;
                channel.sendMessage(new PartMsg(Arrays.copyOf(nextBytes, read)), null);
            } else {
                channel.sendMessage(new ByeMsg(total), null);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void messageFailed(FTPMessage msg, Host to, Throwable cause) {

    }

    @Override
    public void deliverEvent(ChannelEvent evt) {
        if (evt instanceof ServerUpEvent){
            try {
                startTransfer(path);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                System.exit(1);
            }
        } else if (evt instanceof ServerDownEvent){
            logger.info("Server connection lost");
            System.exit(1);
        } else if (evt instanceof ServerFailedEvent){
            logger.info("Server connection failed");
            System.exit(1);
        }
    }

    public static void main(String[] args) throws Exception {
        new Client(args);
    }
}
