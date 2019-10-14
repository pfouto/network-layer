package network;

import io.netty.buffer.ByteBuf;

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
     */
    void serialize(T t, ByteBuf out);

    /**
     * Deserializes an object from a byte buffer and returns it.
     *
     * @param in The byte buffer which contains the object to be deserialized
     * @return The deserialized object
     * @throws UnknownHostException if the message to be deserialized contains a host that cannot be deserialized. Should never be thrown (visible for testing).
     */
    T deserialize(ByteBuf in) throws UnknownHostException;

    /**
     * Calculates the serialized size of a given object2
     *
     * @param t The object whose size is to be calculated
     * @return The size of the object when serialized
     */
    int serializedSize(T t);
}
