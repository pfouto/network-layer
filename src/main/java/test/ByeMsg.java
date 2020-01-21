package test;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public class ByeMsg extends FTPMessage {

    final long size;

    public ByeMsg(long size){
        super(Type.BYE);
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "ByeMsg{" +
                "size=" + size +
                '}';
    }

    static FTPSerializer<FTPMessage> serializer = new FTPSerializer<FTPMessage>() {
        @Override
        public void serialize(FTPMessage ftpMessage, ByteBuf out) {
            ByeMsg byeMsg = (ByeMsg) ftpMessage;
            out.writeLong(byeMsg.size);
        }

        @Override
        public ByeMsg deserialize(ByteBuf in) {
            return new ByeMsg(in.readLong());
        }
    };
}
