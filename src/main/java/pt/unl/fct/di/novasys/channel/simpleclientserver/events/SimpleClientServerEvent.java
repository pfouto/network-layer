package pt.unl.fct.di.novasys.channel.simpleclientserver.events;

import pt.unl.fct.di.novasys.channel.ChannelEvent;

public abstract class SimpleClientServerEvent extends ChannelEvent {

    SimpleClientServerEvent(short id){
        super(id);
    }
}
