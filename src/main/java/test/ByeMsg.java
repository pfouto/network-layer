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

    static FTPSerializer serializer = new FTPSerializer<ByeMsg>() {
        @Override
        public void serialize(ByeMsg byeMsg, ByteBuf out) throws IOException {
            out.writeLong(byeMsg.size);
        }

        @Override
        public ByeMsg deserialize(ByteBuf in) throws IOException {
            return new ByeMsg(in.readLong());
        }
    };
}
