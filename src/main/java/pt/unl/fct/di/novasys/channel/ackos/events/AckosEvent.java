package pt.unl.fct.di.novasys.channel.ackos.events;

import pt.unl.fct.di.novasys.channel.ChannelEvent;

public abstract class AckosEvent extends ChannelEvent {

    AckosEvent(short id){
        super(id);
    }
}
