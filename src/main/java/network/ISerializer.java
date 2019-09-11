package network;

import io.netty.buffer.ByteBuf;

import java.net.UnknownHostException;

public interface ISerializer<T> {

    void serialize(T t, ByteBuf out);

    T deserialize(ByteBuf in) throws UnknownHostException;

    int serializedSize(T t);
}
