package channel.simpleclientserver.events;

import channel.ChannelEvent;

public abstract class SimpleClientServerEvent extends ChannelEvent {

    SimpleClientServerEvent(short id){
        super(id);
    }
}
