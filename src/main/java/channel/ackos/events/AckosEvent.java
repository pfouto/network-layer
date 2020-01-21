package channel.ackos.events;

import channel.ChannelEvent;

public abstract class AckosEvent extends ChannelEvent {

    AckosEvent(short id){
        super(id);
    }
}
