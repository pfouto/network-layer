package test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HelloMsg extends FTPMessage {

    public final String path;

    public HelloMsg(String path){
        super(Type.HELLO);
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "HelloMsg{" +
                "path='" + path + '\'' +
                '}';
    }

    static FTPSerializer<FTPMessage> serializer = new FTPSerializer<FTPMessage>() {
        @Override
        public void serialize(FTPMessage ftpMessage, ByteBuf out) {
            HelloMsg helloMsg = (HelloMsg) ftpMessage;
            int size = ByteBufUtil.utf8Bytes(helloMsg.path);
            out.writeInt(size);
            ByteBufUtil.reserveAndWriteUtf8(out, helloMsg.path, size);
        }

        @Override
        public HelloMsg deserialize(ByteBuf in) {
            int size = in.readInt();
            String path = in.readCharSequence(size, StandardCharsets.UTF_8).toString();
            return new HelloMsg(path);
        }
    };
}
