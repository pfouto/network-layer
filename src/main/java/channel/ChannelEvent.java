package channel;

public abstract class ChannelEvent<T> {

    private final short id;

    public ChannelEvent(short id){
        this.id = id;
    }

    public short getId() {
        return id;
    }
}