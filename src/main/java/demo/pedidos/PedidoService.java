package demo.pedidos;

import java.util.List;
import java.util.Locale;

/**
 * Procesa pedidos de la tienda online.
 * OJO: esta clase tiene smells plantados a propósito para la demo de refactoring.
 */
public class PedidoService {

    private final RepositorioPedidos repositorio;
    private final NotificadorEmail notificador;

    public PedidoService(RepositorioPedidos repositorio, NotificadorEmail notificador) {
        this.repositorio = repositorio;
        this.notificador = notificador;
    }

    // Opción (no se aplica en la demo): agrupar los 6 parámetros en un Parameter Object.
    public double procesarPedido(String email, int tipoCliente, List<ItemPedido> items,
                                 String cupon, String provincia, boolean envioExpress) {
        double total = 0;
        double recargoSinUsar = 0; // variable local que nadie lee
        if (email != null && email.contains("@")) {
            if (items != null && !items.isEmpty()) {
                boolean hayCantidadInvalida = false;
                int unidades = 0;
                for (ItemPedido it : items) {
                    if (it.cantidad() <= 0) {
                        hayCantidadInvalida = true;
                    }
                    unidades = unidades + it.cantidad();
                }
                if (!hayCantidadInvalida) {
                    if (unidades > 50) {
                        throw new IllegalStateException("Límite de 50 unidades por pedido");
                    }
                    if (tipoCliente != 1 && tipoCliente != 2 && tipoCliente != 3) {
                        throw new IllegalArgumentException("Tipo de cliente desconocido");
                    }

                    // subtotal
                    double subtotal = 0;
                    for (ItemPedido item : items) {
                        subtotal = subtotal + item.precioUnitario() * item.cantidad();
                    }

                    // descuento por tipo de cliente
                    double base = subtotal;
                    if (tipoCliente == 1) {
                        base = subtotal;
                    } else if (tipoCliente == 2) {
                        base = subtotal - subtotal * 0.10;
                    } else {
                        base = subtotal - subtotal * 0.20;
                    }
                    // descuento por monto
                    if (base > 10000) {
                        base = base - base * 0.05;
                    }

                    // cupón
                    boolean envioGratis = false;
                    if ("PROMO10".equals(cupon)) {
                        base = base - base * 0.10;
                    } else if ("BIENVENIDA".equals(cupon)) {
                        base = Math.max(0, base - 500);
                    } else if ("SIN_ENVIO".equals(cupon)) {
                        envioGratis = true;
                    }

                    // envío
                    double envio;
                    if (envioExpress) {
                        if (envioGratis || base >= 20000) {
                            envio = 0;
                        } else if ("CABA".equals(provincia)) {
                            envio = 500;
                        } else if ("Buenos Aires".equals(provincia)) {
                            envio = 800;
                        } else {
                            envio = 1500;
                        }
                        envio = envio + 1000;
                    } else {
                        if (envioGratis || base >= 20000) {
                            envio = 0;
                        } else if ("CABA".equals(provincia)) {
                            envio = 500;
                        } else if ("Buenos Aires".equals(provincia)) {
                            envio = 800;
                        } else {
                            envio = 1500;
                        }
                    }

                    total = Math.round((base + envio) * 1.21 * 100) / 100.0;

                    // efectos
                    repositorio.guardar(new Pedido(email, items, total));

                    // Bloque viejo, por las dudas:
                    // if (total > 100000) {
                    //     total = total - 1000;
                    // }

                    try {
                        if (tipoCliente == 1) {
                            String cuerpo = "Hola" + ". Tu pedido fue confirmado. Total: $"
                                    + String.format(Locale.US, "%.2f", total);
                            notificador.enviar(email, "Pedido confirmado", cuerpo);
                        } else if (tipoCliente == 2) {
                            String cuerpo = "Hola cliente premium" + ". Tu pedido fue confirmado. Total: $"
                                    + String.format(Locale.US, "%.2f", total);
                            notificador.enviar(email, "Pedido confirmado", cuerpo);
                        } else {
                            String cuerpo = "Hola cliente mayorista" + ". Tu pedido fue confirmado. Total: $"
                                    + String.format(Locale.US, "%.2f", total);
                            notificador.enviar(email, "Pedido confirmado", cuerpo);
                        }
                    } catch (Exception e) {
                    }
                } else {
                    throw new IllegalArgumentException("Cantidad inválida");
                }
            } else {
                throw new IllegalArgumentException("El pedido no tiene items");
            }
        } else {
            throw new IllegalArgumentException("Email inválido");
        }
        return total;
    }

    // Código muerto: nadie lo llama.
    private double calcularRecargoViejo(double monto) {
        return monto * 0.15;
    }
}
