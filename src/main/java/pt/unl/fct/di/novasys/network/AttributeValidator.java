package pt.unl.fct.di.novasys.network;

import pt.unl.fct.di.novasys.network.data.Attributes;

public interface AttributeValidator {

    String CHANNEL_MAGIC_ATTRIBUTE = "magic_number";

    AttributeValidator ALWAYS_VALID = attr -> true;

    boolean validateAttributes(Attributes attr);
}

