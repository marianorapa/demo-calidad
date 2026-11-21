package demo.pedidos;

import java.util.ArrayList;
import java.util.List;

/** Doble de prueba: registra los mails que se intentaron enviar. */
class NotificadorEspia implements NotificadorEmail {

    record Mail(String destino, String asunto, String cuerpo) {
    }

    private final List<Mail> enviados = new ArrayList<>();

    @Override
    public void enviar(String destino, String asunto, String cuerpo) {
        enviados.add(new Mail(destino, asunto, cuerpo));
    }

    List<Mail> mailsEnviados() {
        return enviados;
    }
}
