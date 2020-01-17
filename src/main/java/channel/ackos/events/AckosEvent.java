package channel.ackos.events;

import channel.ChannelEvent;

public abstract class AckosEvent<T> extends ChannelEvent<T> {

    AckosEvent(short id){
        super(id);
    }
}
