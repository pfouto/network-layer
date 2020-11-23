package pt.unl.fct.di.novasys.network.userevents;

import pt.unl.fct.di.novasys.network.data.Attributes;

public class HandshakeCompleted {

    private Attributes attr;

    public HandshakeCompleted(Attributes attr){
        this.attr = attr;
    }

    public Attributes getAttr() {
        return attr;
    }
}
