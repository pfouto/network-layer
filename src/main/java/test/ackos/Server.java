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
import java.util.Properties;

public class Server implements ChannelListener<FTPMessage> {

    static {
        System.setProperty("log4j.configurationFile", "log4j2.xml");
    }

    private static final Logger logger = LogManager.getLogger(Server.class);

    private IChannel<FTPMessage> channel;
    private FileOutputStream fos;

    public Server() throws Exception {
        Properties props = new Properties();
        props.setProperty("address", "localhost");
        channel = new AckosChannel<>(FTPMessage.serializer, this, props);
    }

    @Override
    public void deliverMessage(FTPMessage msg, Host from) {
        logger.info("Message: " + msg + " : " + from);
        try {
            if (msg instanceof HelloMsg) {
                fos = new FileOutputStream("output/" + ((HelloMsg) msg).path);
            } else if (msg instanceof PartMsg) {
                PartMsg pm = (PartMsg) msg;
                fos.write(pm.bytes);
            } else {
                ByeMsg bm = (ByeMsg) msg;
                fos.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void messageSent(FTPMessage msg, Host to) {
        logger.info("Ack: " + msg);
    }

    @Override
    public void messageFailed(FTPMessage msg, Host to, Throwable cause) {
        logger.info("Message failed: " + msg);
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
        new Server();
    }
}
