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
import java.util.Collections;
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
        channel = new AckosChannel<>(FTPMessage.serializer, this,props);
    }

    @Override
    public void deliverMessage(FTPMessage msg, Host from) {
        logger.info("Message: " + msg + " : " + from);
        try {
            if (msg instanceof HelloMsg) {
                fos = new FileOutputStream("output/" + ((HelloMsg) msg).path);
            } else if (msg instanceof PartMsg){
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


    private void onMessageAck(MessageAckEvent<FTPMessage> evt) {
        logger.info("Ack: " + evt);
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
        new Server();
    }
}
