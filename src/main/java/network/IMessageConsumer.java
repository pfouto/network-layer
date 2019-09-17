package network;

public interface IMessageConsumer<T> {

    void deliverMessage(short msgCode, T msg, Host from);
}
