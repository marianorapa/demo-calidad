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
    private final PoliticaDeDescuento politicaDeDescuento = new PoliticaDeDescuento();

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

        double subtotal = calcularSubtotal(items);

        double base = politicaDeDescuento.calcularBase(subtotal, tipoCliente);

        // cupón
        boolean envioGratis = false;
        if ("PROMO10".equals(cupon)) {
            base = base - base * DESCUENTO_CUPON_PROMO10;
        } else if ("BIENVENIDA".equals(cupon)) {
            base = Math.max(0, base - DESCUENTO_CUPON_BIENVENIDA);
        } else if ("SIN_ENVIO".equals(cupon)) {
            envioGratis = true;
        }

        double envio = calcularEnvio(base, envioGratis, provincia, envioExpress);

        double total = Math.round((base + envio) * FACTOR_IVA * 100) / 100.0;

        // efectos
        repositorio.guardar(new Pedido(email, items, total));

        // Bloque viejo, por las dudas:
        // if (total > 100000) {
        //     total = total - 1000;
        // }

        try {
            String cuerpo = armarCuerpoMail(tipoCliente, total);
            notificador.enviar(email, "Pedido confirmado", cuerpo);
        } catch (Exception e) {
        }
        return total;
    }

    private double calcularSubtotal(List<ItemPedido> items) {
        double subtotal = 0;
        for (ItemPedido item : items) {
            subtotal = subtotal + item.precioUnitario() * item.cantidad();
        }
        return subtotal;
    }

    private double calcularEnvio(double base, boolean envioGratis, String provincia, boolean envioExpress) {
        double envio = 0;
        if (!envioGratis && base < UMBRAL_ENVIO_GRATIS) {
            envio = tarifaPorProvincia(provincia);
        }
        if (envioExpress) {
            envio = envio + RECARGO_EXPRESS;
        }
        return envio;
    }

    private double tarifaPorProvincia(String provincia) {
        if ("CABA".equals(provincia)) {
            return ENVIO_CABA;
        } else if ("Buenos Aires".equals(provincia)) {
            return ENVIO_BUENOS_AIRES;
        }
        return ENVIO_RESTO_DEL_PAIS;
    }

    private String armarCuerpoMail(int tipoCliente, double total) {
        String saludo;
        if (tipoCliente == CLIENTE_REGULAR) {
            saludo = "Hola";
        } else if (tipoCliente == CLIENTE_PREMIUM) {
            saludo = "Hola cliente premium";
        } else {
            saludo = "Hola cliente mayorista";
        }
        return saludo + ". Tu pedido fue confirmado. Total: $" + String.format(Locale.US, "%.2f", total);
    }

    // Código muerto: nadie lo llama.
    private double calcularRecargoViejo(double monto) {
        return monto * 0.15;
    }
}
