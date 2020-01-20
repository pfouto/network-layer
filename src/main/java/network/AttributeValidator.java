package network;

import network.data.Attributes;

public interface AttributeValidator {

    AttributeValidator ALWAYS_VALID = attr -> true;

    boolean validateAttributes(Attributes attr);
}

