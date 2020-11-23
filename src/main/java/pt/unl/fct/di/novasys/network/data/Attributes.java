package pt.unl.fct.di.novasys.network.data;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

//TODO there are probably better implementations of this elsewhere...
public class Attributes {

    public static Attributes EMPTY = new Attributes();

    private Map<String, byte[]> attrMap;

    public Attributes() {
        this(new HashMap<>());
    }

    public Attributes(Map<String, byte[]> initialValues) {
        this.attrMap = new HashMap<>(initialValues);
    }

    public Attributes shallowClone() {
        return new Attributes(new HashMap<>(attrMap));
    }

    public Attributes deepClone() {
        Map<String, byte[]> newMap = new HashMap<>();
        for (Map.Entry<String, byte[]> e : attrMap.entrySet()) {
            newMap.put(e.getKey(), Arrays.copyOf(e.getValue(), e.getValue().length));
        }
        return new Attributes(newMap);
    }

    public Set<String> getKeys() {
        return attrMap.keySet();
    }

    public boolean containsKey(String key) {
        return attrMap.containsKey(key);
    }

    public <T> void putObject(String key, T value, ISerializer<T> serializer) throws IOException {
        ByteBuf buffer = Unpooled.buffer();
        serializer.serialize(value, buffer);
        attrMap.put(key, buffer.array());
    }

    public <T> T getObject(String key, ISerializer<T> serializer) throws IOException {
        byte[] bytes = attrMap.get(key);
        if (bytes == null) return null;
        ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
        return serializer.deserialize(byteBuf);
    }

    public void putBoolean(String key, boolean value) {
        attrMap.put(key, new byte[]{(byte) (value ? 1 : 0)});
    }

    public Boolean getBoolean(String key) {
        byte[] bytes = attrMap.get(key);
        if (bytes == null) return null;
        return bytes[0] != 0;
    }

    public void putShort(String key, short value) {
        attrMap.put(key, ByteBuffer.allocate(Short.BYTES).putShort(value).array());
    }

    public Short getShort(String key) {
        byte[] bytes = attrMap.get(key);
        if (bytes == null) return null;
        return ByteBuffer.wrap(bytes).getShort();
    }

    public void putInt(String key, int value) {
        attrMap.put(key, ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    public Integer getInt(String key) {
        byte[] bytes = attrMap.get(key);
        if (bytes == null) return null;
        return ByteBuffer.wrap(bytes).getInt();
    }

    public void putLong(String key, long value) {
        attrMap.put(key, ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    public Long getLong(String key) {
        byte[] bytes = attrMap.get(key);
        if (bytes == null) return null;
        return ByteBuffer.wrap(bytes).getLong();
    }

    public void putFloat(String key, float value) {
        attrMap.put(key, ByteBuffer.allocate(Float.BYTES).putFloat(value).array());
    }

    public Float getFloat(String key) {
        byte[] bytes = attrMap.get(key);
        if (bytes == null) return null;
        return ByteBuffer.wrap(bytes).getFloat();
    }

    public void putString(String key, String value) {
        attrMap.put(key, value.getBytes(StandardCharsets.UTF_8));
    }

    public String getString(String key) {
        byte[] bytes = attrMap.get(key);
        if (bytes == null) return null;
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public void putHost(String key, Host value) throws IOException {
        ByteBuf buffer = Unpooled.buffer(6);
        Host.serializer.serialize(value, buffer);
        attrMap.put(key, buffer.array());
    }

    public Host getHost(String key) throws IOException {
        byte[] bytes = attrMap.get(key);
        if (bytes == null) return null;
        ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
        return Host.serializer.deserialize(byteBuf);
    }

    @Override
    public String toString() {
        return "Attributes{" +
                attrMap.keySet() +
                '}';
    }

    public static ISerializer<Attributes> serializer = new ISerializer<Attributes>() {
        @Override
        public void serialize(Attributes attributes, ByteBuf out) {
            out.writeInt(attributes.attrMap.size());
            for (Map.Entry<String, byte[]> e : attributes.attrMap.entrySet()) {
                int keySize = ByteBufUtil.utf8Bytes(e.getKey());
                out.writeInt(keySize);
                ByteBufUtil.reserveAndWriteUtf8(out, e.getKey(), keySize);
                out.writeInt(e.getValue().length);
                out.writeBytes(e.getValue());
            }
        }

        @Override
        public Attributes deserialize(ByteBuf in) {
            int mapSize = in.readInt();
            Map<String, byte[]> attrMap = new HashMap<>();
            for (int i = 0; i < mapSize; i++) {
                int keySize = in.readInt();
                String key = in.readCharSequence(keySize, StandardCharsets.UTF_8).toString();
                int valueSize = in.readInt();
                byte[] bytes = new byte[valueSize];
                in.readBytes(bytes);
                attrMap.put(key, bytes);
            }
            return new Attributes(attrMap);
        }
    };
}
