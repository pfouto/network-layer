package pt.unl.fct.di.novasys.channel.accrual.events;

import pt.unl.fct.di.novasys.channel.ChannelEvent;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Map;

public class PhiEvent extends ChannelEvent {

    public static final short EVENT_ID = 300;

    private final Map<Host, Map<String, Double>> values;

    public PhiEvent(Map<Host, Map<String, Double>> values) {
        super(EVENT_ID);
        this.values = values;
    }

    public Map<Host, Map<String, Double>> getValues() {
        return values;
    }
}
