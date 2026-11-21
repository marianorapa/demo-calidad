package demo.pedidos;

import java.util.List;

public record Pedido(String email, List<ItemPedido> items, double total) {
}
