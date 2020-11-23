package pt.unl.fct.di.novasys.network.data;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * Represents a node in the network layer, including its IP Address and listen port
 *
 * @author pfouto
 */
public class Host implements Comparable<Host> {
    private final int port;
    private final InetAddress address;
    private final byte[] addressBytes;

    /**
     * Creates a new host with the given address and port
     *
     * @param address The address of the host to create
     * @param port    The port of the host to create
     */
    public Host(InetAddress address, int port) {
        this(address, address.getAddress(), port);
    }

    private Host(InetAddress address, byte[] addressBytes, int port) {
        if (!(address instanceof Inet4Address))
            throw new AssertionError(address + " not and IPv4 address");
        this.address = address;
        this.port = port;
        this.addressBytes = addressBytes;
        assert addressBytes.length == 4;
    }

    /**
     * Gets the address of this host
     * @return The INetAddress
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Gets the port of this host
     * @return  The port
     */
    public int getPort() {
        return port;
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

    //Assume always a valid IPv4 address
    @Override
    public int compareTo(Host other) {
        for (int i = 0; i < 4; i++) {
            int cmp = Byte.compare(this.addressBytes[i], other.addressBytes[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(this.port, other.port);
    }

    public static ISerializer<Host> serializer = new ISerializer<Host>() {
        @Override
        public void serialize(Host host, ByteBuf out) {
            out.writeBytes(host.addressBytes);
            out.writeShort(host.port);
        }

        @Override
        public Host deserialize(ByteBuf in) throws UnknownHostException {
            byte[] addrBytes = new byte[4];
            in.readBytes(addrBytes);
            int port = in.readShort() & 0xFFFF;
            return new Host(InetAddress.getByAddress(addrBytes), addrBytes, port);
        }
    };

}
