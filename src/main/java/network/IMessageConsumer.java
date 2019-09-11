package network;

public interface IMessageConsumer<T> {

    void deliverMessage(byte msgCode, T msg, Host from);
}
