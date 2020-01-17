package network.userevents;

import network.data.Attributes;

public class HandshakeCompleted {

    private Attributes attr;

    public HandshakeCompleted(Attributes attr){
        this.attr = attr;
    }

    public Attributes getAttr() {
        return attr;
    }
}
