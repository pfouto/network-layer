package network;

import network.data.Attributes;

public interface AttributeValidator {

    String CHANNEL_MAGIC_ATTRIBUTE = "magic_number";

    AttributeValidator ALWAYS_VALID = attr -> true;

    boolean validateAttributes(Attributes attr);
}

