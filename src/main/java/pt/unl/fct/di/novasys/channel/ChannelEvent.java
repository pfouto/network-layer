package pt.unl.fct.di.novasys.channel;

public abstract class ChannelEvent {

    private final short id;

    public ChannelEvent(short id){
        this.id = id;
    }

    public short getId() {
        return id;
    }
}