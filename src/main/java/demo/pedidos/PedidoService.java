package demo.pedidos;

import java.util.List;
import java.util.Locale;

/**
 * Procesa pedidos de la tienda online.
 * OJO: esta clase tiene smells plantados a propósito para la demo de refactoring.
 */
public class PedidoService {

    // Límite fijo: hoy no hay plan B si un cliente necesita más unidades.
    private static final int LIMITE_UNIDADES = 50;

    private static final int CLIENTE_REGULAR = 1;
    private static final int CLIENTE_PREMIUM = 2;
    private static final int CLIENTE_MAYORISTA = 3;

    private static final double DESCUENTO_PREMIUM = 0.10;
    private static final double DESCUENTO_MAYORISTA = 0.20;

    private static final double UMBRAL_DESCUENTO_POR_MONTO = 10000;
    private static final double DESCUENTO_POR_MONTO = 0.05;

    private static final double DESCUENTO_CUPON_PROMO10 = 0.10;
    private static final double DESCUENTO_CUPON_BIENVENIDA = 500;

    private static final double UMBRAL_ENVIO_GRATIS = 20000;
    private static final double ENVIO_CABA = 500;
    private static final double ENVIO_BUENOS_AIRES = 800;
    private static final double ENVIO_RESTO_DEL_PAIS = 1500;
    private static final double RECARGO_EXPRESS = 1000;

    private static final double FACTOR_IVA = 1.21;

    private final RepositorioPedidos repositorio;
    private final NotificadorEmail notificador;

    public PedidoService(RepositorioPedidos repositorio, NotificadorEmail notificador) {
        this.repositorio = repositorio;
        this.notificador = notificador;
    }

    // Opción (no se aplica en la demo): agrupar los 6 parámetros en un Parameter Object.
    public double procesarPedido(String email, int tipoCliente, List<ItemPedido> items,
                                 String cupon, String provincia, boolean envioExpress) {
        double recargoSinUsar = 0; // variable local que nadie lee
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Email inválido");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("El pedido no tiene items");
        }
        boolean hayCantidadInvalida = false;
        int unidades = 0;
        for (ItemPedido it : items) {
            if (it.cantidad() <= 0) {
                hayCantidadInvalida = true;
            }
            unidades = unidades + it.cantidad();
        }
        if (hayCantidadInvalida) {
            throw new IllegalArgumentException("Cantidad inválida");
        }
        if (unidades > LIMITE_UNIDADES) {
            throw new IllegalStateException("Límite de " + LIMITE_UNIDADES + " unidades por pedido");
        }
        if (tipoCliente != CLIENTE_REGULAR && tipoCliente != CLIENTE_PREMIUM
                && tipoCliente != CLIENTE_MAYORISTA) {
            throw new IllegalArgumentException("Tipo de cliente desconocido");
        }

        // subtotal
        double subtotal = 0;
        for (ItemPedido item : items) {
            subtotal = subtotal + item.precioUnitario() * item.cantidad();
        }

        // descuento por tipo de cliente
        double base = subtotal;
        if (tipoCliente == CLIENTE_REGULAR) {
            base = subtotal;
        } else if (tipoCliente == CLIENTE_PREMIUM) {
            base = subtotal - subtotal * DESCUENTO_PREMIUM;
        } else {
            base = subtotal - subtotal * DESCUENTO_MAYORISTA;
        }
        // descuento por monto
        if (base > UMBRAL_DESCUENTO_POR_MONTO) {
            base = base - base * DESCUENTO_POR_MONTO;
        }

        // cupón
        boolean envioGratis = false;
        if ("PROMO10".equals(cupon)) {
            base = base - base * DESCUENTO_CUPON_PROMO10;
        } else if ("BIENVENIDA".equals(cupon)) {
            base = Math.max(0, base - DESCUENTO_CUPON_BIENVENIDA);
        } else if ("SIN_ENVIO".equals(cupon)) {
            envioGratis = true;
        }

        // envío
        double envio;
        if (envioExpress) {
            if (envioGratis || base >= UMBRAL_ENVIO_GRATIS) {
                envio = 0;
            } else if ("CABA".equals(provincia)) {
                envio = ENVIO_CABA;
            } else if ("Buenos Aires".equals(provincia)) {
                envio = ENVIO_BUENOS_AIRES;
            } else {
                envio = ENVIO_RESTO_DEL_PAIS;
            }
            envio = envio + RECARGO_EXPRESS;
        } else {
            if (envioGratis || base >= UMBRAL_ENVIO_GRATIS) {
                envio = 0;
            } else if ("CABA".equals(provincia)) {
                envio = ENVIO_CABA;
            } else if ("Buenos Aires".equals(provincia)) {
                envio = ENVIO_BUENOS_AIRES;
            } else {
                envio = ENVIO_RESTO_DEL_PAIS;
            }
        }

        double total = Math.round((base + envio) * FACTOR_IVA * 100) / 100.0;

        // efectos
        repositorio.guardar(new Pedido(email, items, total));

        // Bloque viejo, por las dudas:
        // if (total > 100000) {
        //     total = total - 1000;
        // }

        try {
            if (tipoCliente == CLIENTE_REGULAR) {
                String cuerpo = "Hola" + ". Tu pedido fue confirmado. Total: $"
                        + String.format(Locale.US, "%.2f", total);
                notificador.enviar(email, "Pedido confirmado", cuerpo);
            } else if (tipoCliente == CLIENTE_PREMIUM) {
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
        return total;
    }

    // Código muerto: nadie lo llama.
    private double calcularRecargoViejo(double monto) {
        return monto * 0.15;
    }
}
