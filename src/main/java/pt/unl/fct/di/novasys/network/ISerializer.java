package pt.unl.fct.di.novasys.network;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.net.UnknownHostException;

/**
 * Represents a serializer/deserializer that writes/reads objects to/from a byte buffer
 *
 * @param <T> The type of the object which this class serializes
 * @author pfouto
 */
public interface ISerializer<T> {

    /**
     * Serializes the received object into the received byte buffer.
     *
     * @param t   The object to serialize
     * @param out The byte buffer to which the object will the written
     * @throws IOException if the serialization fails
     */
    void serialize(T t, ByteBuf out) throws IOException;

    /**
     * Deserializes an object from a byte buffer and returns it.
     *
     * @param in The byte buffer which contains the object to be deserialized
     * @return The deserialized object
     * @throws IOException if the deserialization fails
     */
    T deserialize(ByteBuf in) throws IOException;

}
