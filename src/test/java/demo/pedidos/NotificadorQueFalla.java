package demo.pedidos;

/** Doble de prueba: simula un servidor de mail caído. */
class NotificadorQueFalla implements NotificadorEmail {

    @Override
    public void enviar(String destino, String asunto, String cuerpo) {
        throw new RuntimeException("Servidor de mail caído");
    }
}
