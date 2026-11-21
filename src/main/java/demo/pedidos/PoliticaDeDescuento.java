package demo.pedidos;

/** Descuentos automáticos: por tipo de cliente y por monto. */
class PoliticaDeDescuento {

    private static final int CLIENTE_REGULAR = 1;
    private static final int CLIENTE_PREMIUM = 2;

    private static final double DESCUENTO_PREMIUM = 0.10;
    private static final double DESCUENTO_MAYORISTA = 0.20;

    private static final double UMBRAL_DESCUENTO_POR_MONTO = 10000;
    private static final double DESCUENTO_POR_MONTO = 0.05;

    double calcularBase(double subtotal, int tipoCliente) {
        double base;
        if (tipoCliente == CLIENTE_REGULAR) {
            base = subtotal;
        } else if (tipoCliente == CLIENTE_PREMIUM) {
            base = subtotal - subtotal * DESCUENTO_PREMIUM;
        } else {
            base = subtotal - subtotal * DESCUENTO_MAYORISTA;
        }
        if (base > UMBRAL_DESCUENTO_POR_MONTO) {
            base = base - base * DESCUENTO_POR_MONTO;
        }
        return base;
    }
}
