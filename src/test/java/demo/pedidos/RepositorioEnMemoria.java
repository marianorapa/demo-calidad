package demo.pedidos;

import java.util.ArrayList;
import java.util.List;

/** Doble de prueba: guarda los pedidos en una lista. */
class RepositorioEnMemoria implements RepositorioPedidos {

    private final List<Pedido> pedidos = new ArrayList<>();

    @Override
    public void guardar(Pedido pedido) {
        pedidos.add(pedido);
    }

    List<Pedido> pedidosGuardados() {
        return pedidos;
    }
}
