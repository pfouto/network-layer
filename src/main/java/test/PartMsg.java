package test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class PartMsg extends FTPMessage {

    public final byte[] bytes;

    public PartMsg(byte[] bytes){
        super(Type.PART);
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return "PartMsg{" +
                "bytes=" + bytes.length +
                '}';
    }

    static FTPSerializer<FTPMessage> serializer = new FTPSerializer<FTPMessage>() {
        @Override
        public void serialize(FTPMessage ftpMessage, ByteBuf out)  {
            PartMsg partMsg = (PartMsg) ftpMessage;
            out.writeInt(partMsg.bytes.length);
            out.writeBytes(partMsg.bytes);
        }

        @Override
        public PartMsg deserialize(ByteBuf in)  {
            int size = in.readInt();
            byte[] bytes = new byte[size];
            in.readBytes(bytes);
            return new PartMsg(bytes);
        }
    };
}
