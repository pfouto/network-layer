package network;

import io.netty.buffer.ByteBuf;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class Host implements Comparable<Host> {
    private final int port;
    private final InetAddress address;
    private final byte[] addressBytes;

    public Host(InetAddress address, int port) {
        this(address, address.getAddress(), port);
    }

    public Host(InetAddress address, byte[] addressBytes, int port) {
        if(!(address instanceof Inet4Address))
            throw new AssertionError(address + " not and IPv4 address");
        this.address = address;
        this.port = port;
        this.addressBytes = addressBytes;
        assert addressBytes.length == 4;
    }

    @Override
    public String toString() {
        return address.getHostAddress() + ":" + port;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof Host)) return false;
        Host o = (Host) other;
        return o.port == port && o.address.equals(address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(port, address);
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public void serialize(ByteBuf out) {
        out.writeBytes(addressBytes);
        out.writeShort(port);
    }

    public static Host deserialize(ByteBuf in) throws UnknownHostException {
        byte[] addrBytes = new byte[4];
        in.readBytes(addrBytes);
        int port = in.readShort() & 0xFFFF;
        return new Host(InetAddress.getByAddress(addrBytes), addrBytes, port);
    }

    public int serializedSize() {
        return 4 + 2;
    }

    //Assume always a valid IPv4 address
    @Override
    public int compareTo(Host other) {
        for (int i = 0; i < 4; i++) {
            int cmp = Byte.compare(this.addressBytes[i], other.addressBytes[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(this.port, other.port);
    }
}
