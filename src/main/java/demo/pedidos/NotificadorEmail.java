package demo.pedidos;

public interface NotificadorEmail {
    void enviar(String destino, String asunto, String cuerpo);
}
