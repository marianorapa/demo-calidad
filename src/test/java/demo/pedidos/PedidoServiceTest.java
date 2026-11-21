package demo.pedidos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Red de seguridad del refactoring: solo usa la API pública de PedidoService.
 * Los valores esperados salen de calcular a mano las reglas del README.
 */
class PedidoServiceTest {

    private static final double DELTA = 0.01;
    private static final String EMAIL = "ana@tienda.com";
    private static final int REGULAR = 1;
    private static final int PREMIUM = 2;
    private static final int MAYORISTA = 3;

    private RepositorioEnMemoria repositorio;
    private NotificadorEspia notificador;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioEnMemoria();
        notificador = new NotificadorEspia();
    }

    // ---------- helpers ----------

    private PedidoService servicio() {
        return new PedidoService(repositorio, notificador);
    }

    private static ItemPedido unItem(double precio, int cantidad) {
        return new ItemPedido("Item", precio, cantidad);
    }

    private static List<ItemPedido> unItem(double precio) {
        return List.of(unItem(precio, 1));
    }

    private double procesar(int tipo, List<ItemPedido> items, String cupon, String provincia, boolean express) {
        return servicio().procesarPedido(EMAIL, tipo, items, cupon, provincia, express);
    }

    private double procesarSimple(int tipo, List<ItemPedido> items, String provincia) {
        return procesar(tipo, items, null, provincia, false);
    }

    private void verificarRechazo(Class<? extends RuntimeException> tipo, String mensaje,
                                  String email, int tipoCliente, List<ItemPedido> items) {
        RuntimeException e = assertThrows(tipo,
                () -> servicio().procesarPedido(email, tipoCliente, items, null, "CABA", false));
        assertEquals(mensaje, e.getMessage());
        assertTrue(repositorio.pedidosGuardados().isEmpty(), "no debe guardarse ningún pedido");
        assertTrue(notificador.mailsEnviados().isEmpty(), "no debe enviarse ningún mail");
    }

    private static List<ItemPedido> casoRegular() {
        return List.of(new ItemPedido("Teclado", 5000, 1));
    }

    private static List<ItemPedido> casoPremium() {
        return List.of(new ItemPedido("Teclado", 5000, 1), new ItemPedido("Mouse", 2000, 2));
    }

    private static List<ItemPedido> casoMayorista() {
        return List.of(new ItemPedido("Monitor", 30000, 1));
    }

    // ---------- tests ----------

    @Nested
    @DisplayName("Validaciones")
    class Validaciones {

        @ParameterizedTest(name = "email \"{0}\" es inválido")
        @NullSource
        @ValueSource(strings = {"sin-arroba", ""})
        void rechazaEmailNuloOSinArroba(String emailInvalido) {
            verificarRechazo(IllegalArgumentException.class, "Email inválido",
                    emailInvalido, REGULAR, unItem(1000));
        }

        @Test
        void rechazaItemsNulos() {
            verificarRechazo(IllegalArgumentException.class, "El pedido no tiene items",
                    EMAIL, REGULAR, null);
        }

        @Test
        void rechazaItemsVacios() {
            verificarRechazo(IllegalArgumentException.class, "El pedido no tiene items",
                    EMAIL, REGULAR, List.of());
        }

        @Test
        void rechazaCantidadCero() {
            verificarRechazo(IllegalArgumentException.class, "Cantidad inválida",
                    EMAIL, REGULAR, List.of(unItem(1000, 0)));
        }

        @Test
        void rechazaCantidadNegativa() {
            verificarRechazo(IllegalArgumentException.class, "Cantidad inválida",
                    EMAIL, REGULAR, List.of(unItem(1000, -1)));
        }

        @Test
        void rechazaCantidadInvalidaAunqueOtroItemSeaValido() {
            verificarRechazo(IllegalArgumentException.class, "Cantidad inválida",
                    EMAIL, REGULAR, List.of(unItem(1000, 2), unItem(1000, 0)));
        }

        @Test
        void rechazaMasDeCincuentaUnidades() {
            verificarRechazo(IllegalStateException.class, "Límite de 50 unidades por pedido",
                    EMAIL, REGULAR, List.of(unItem(10, 51)));
        }

        @Test
        void rechazaMasDeCincuentaUnidadesSumandoVariosItems() {
            verificarRechazo(IllegalStateException.class, "Límite de 50 unidades por pedido",
                    EMAIL, REGULAR, List.of(unItem(10, 30), unItem(10, 21)));
        }

        @ParameterizedTest(name = "tipo de cliente {0} es desconocido")
        @ValueSource(ints = {4, 0, -1})
        void rechazaTipoDeClienteDesconocido(int tipoDesconocido) {
            verificarRechazo(IllegalArgumentException.class, "Tipo de cliente desconocido",
                    EMAIL, tipoDesconocido, unItem(1000));
        }
    }

    @Nested
    @DisplayName("Descuento por tipo de cliente")
    class DescuentoPorTipoDeCliente {

        @Test
        void clienteRegularNoTieneDescuento() {
            double total = procesarSimple(REGULAR, casoRegular(), "CABA");

            assertEquals(6655.00, total, DELTA);
        }

        @Test
        void aplicaDescuentoPremiumDelDiezPorCiento() {
            double total = procesarSimple(PREMIUM, casoPremium(), "Buenos Aires");

            assertEquals(10769.00, total, DELTA);
        }

        @Test
        void aplicaDescuentoMayoristaDelVeintePorCiento() {
            double total = procesarSimple(MAYORISTA, unItem(5000), "CABA");

            // 5000 - 20% = 4000; + 500 de envío; * 1.21
            assertEquals(5445.00, total, DELTA);
        }

        @Test
        void mayoristaConDescuentoPorMontoYEnvioGratis() {
            double total = procesarSimple(MAYORISTA, casoMayorista(), "Córdoba");

            assertEquals(27588.00, total, DELTA);
        }
    }

    @Nested
    @DisplayName("Descuento por monto")
    class DescuentoPorMonto {

        @Test
        void conBaseExactamenteDiezMilNoHayDescuentoPorMonto() {
            double total = procesarSimple(REGULAR, unItem(10000), "CABA");

            assertEquals(12705.00, total, DELTA);
        }

        @Test
        void conBaseApenasMayorADiezMilHayDescuentoPorMonto() {
            double total = procesarSimple(REGULAR, unItem(10001), "CABA");

            assertEquals(12101.15, total, DELTA);
        }
    }

    @Nested
    @DisplayName("Cupones")
    class Cupones {

        @Test
        void promo10DescuentaDiezPorCiento() {
            double total = procesar(REGULAR, unItem(3000), "PROMO10", "CABA", false);

            assertEquals(3872.00, total, DELTA);
        }

        @Test
        void bienvenidaDescuentaQuinientosPesos() {
            double total = procesar(REGULAR, unItem(3000), "BIENVENIDA", "CABA", false);

            assertEquals(3630.00, total, DELTA);
        }

        @Test
        void bienvenidaNoDejaLaBaseNegativa() {
            double total = procesar(REGULAR, unItem(300), "BIENVENIDA", "CABA", false);

            assertEquals(605.00, total, DELTA);
        }

        @Test
        void sinEnvioEliminaElCostoDeEnvio() {
            double total = procesar(REGULAR, unItem(4000), "SIN_ENVIO", "Córdoba", false);

            assertEquals(4840.00, total, DELTA);
        }

        @ParameterizedTest(name = "cupón \"{0}\" se ignora")
        @NullSource
        @ValueSource(strings = {"NOEXISTE", ""})
        void ignoraCuponNuloVacioODesconocido(String cuponIgnorado) {
            double total = procesar(REGULAR, unItem(3000), cuponIgnorado, "CABA", false);

            assertEquals(4235.00, total, DELTA);
        }

        @Test
        void elCuponDistingueMayusculasDeMinusculas() {
            double total = procesar(REGULAR, unItem(3000), "promo10", "CABA", false);

            assertEquals(4235.00, total, DELTA);
        }
    }

    @Nested
    @DisplayName("Envío")
    class Envio {

        @ParameterizedTest(name = "provincia {0} paga tarifa que da total {1}")
        @CsvSource(value = {
                "CABA, 3025.00",
                "Buenos Aires, 3388.00",
                "Córdoba, 4235.00",
                "null, 4235.00"
        }, nullValues = "null")
        void cobraTarifaSegunProvincia(String provincia, double totalEsperado) {
            double total = procesarSimple(REGULAR, unItem(2000), provincia);

            assertEquals(totalEsperado, total, DELTA);
        }

        @Test
        void conBaseMenorAVeinteMilPagaEnvio() {
            double total = procesarSimple(REGULAR, unItem(21000), "CABA");

            // base 19950 (21000 - 5%) < 20000
            assertEquals(24744.50, total, DELTA);
        }

        @Test
        void conBaseMayorAVeinteMilElEnvioEsGratis() {
            double total = procesarSimple(REGULAR, unItem(22000), "CABA");

            // base 20900 (22000 - 5%) >= 20000
            assertEquals(25289.00, total, DELTA);
        }

        @Test
        void conBaseExactamenteVeinteMilElEnvioEsGratis() {
            // 20000 / 0.95 = 21052.63...: tras el 5% por monto la base da exactamente 20000.0
            double total = procesarSimple(REGULAR, unItem(20000 / 0.95), "CABA");

            assertEquals(24200.00, total, DELTA);
        }

        @Test
        void conBaseExactamenteVeinteMilYExpressElEnvioBaseEsGratis() {
            double total = procesar(REGULAR, unItem(20000 / 0.95), null, "CABA", true);

            // (20000 + 0 + 1000) * 1.21
            assertEquals(25410.00, total, DELTA);
        }

        @Test
        void envioExpressSumaMilPesosAlEnvio() {
            double total = procesar(REGULAR, unItem(3000), null, "CABA", true);

            assertEquals(5445.00, total, DELTA);
        }

        @Test
        void envioExpressSumaMilPesosAunConEnvioGratis() {
            double total = procesar(REGULAR, unItem(22000), null, "CABA", true);

            assertEquals(26499.00, total, DELTA);
        }

        @Test
        void envioExpressConCuponSinEnvioSumaMilPesos() {
            double total = procesar(REGULAR, unItem(4000), "SIN_ENVIO", "Córdoba", true);

            // (4000 + 0 + 1000) * 1.21
            assertEquals(6050.00, total, DELTA);
        }

        @ParameterizedTest(name = "express en {0} suma 1000 a la tarifa y da total {1}")
        @CsvSource(value = {
                "CABA, 4235.00",
                "Buenos Aires, 4598.00",
                "Córdoba, 5445.00",
                "null, 5445.00"
        }, nullValues = "null")
        void envioExpressSumaMilPesosALaTarifaDeLaProvincia(String provincia, double totalEsperado) {
            double total = procesar(REGULAR, unItem(2000), null, provincia, true);

            assertEquals(totalEsperado, total, DELTA);
        }
    }

    @Nested
    @DisplayName("Límite de unidades")
    class LimiteDeUnidades {

        @Test
        void cincuentaUnidadesEsValido() {
            double total = procesarSimple(REGULAR, List.of(unItem(10, 50)), "CABA");

            assertEquals(1210.00, total, DELTA);
        }
    }

    @Nested
    @DisplayName("Efectos")
    class Efectos {

        @Test
        void guardaExactamenteUnPedidoConLosDatosCorrectos() {
            List<ItemPedido> items = casoRegular();

            double total = procesarSimple(REGULAR, items, "CABA");

            assertEquals(1, repositorio.pedidosGuardados().size());
            Pedido guardado = repositorio.pedidosGuardados().get(0);
            assertEquals(EMAIL, guardado.email());
            assertEquals(items, guardado.items());
            assertEquals(total, guardado.total(), DELTA);
        }

        @Test
        void enviaUnSoloMailAlClienteConAsuntoPedidoConfirmado() {
            procesarSimple(REGULAR, casoRegular(), "CABA");

            assertEquals(1, notificador.mailsEnviados().size());
            NotificadorEspia.Mail mail = notificador.mailsEnviados().get(0);
            assertEquals(EMAIL, mail.destino());
            assertEquals("Pedido confirmado", mail.asunto());
        }

        @Test
        void cuerpoDelMailParaClienteRegular() {
            procesarSimple(REGULAR, casoRegular(), "CABA");

            assertEquals("Hola. Tu pedido fue confirmado. Total: $6655.00", cuerpoEnviado());
        }

        @Test
        void cuerpoDelMailParaClientePremium() {
            procesarSimple(PREMIUM, casoPremium(), "Buenos Aires");

            assertEquals("Hola cliente premium. Tu pedido fue confirmado. Total: $10769.00", cuerpoEnviado());
        }

        @Test
        void cuerpoDelMailParaClienteMayorista() {
            procesarSimple(MAYORISTA, casoMayorista(), "Córdoba");

            assertEquals("Hola cliente mayorista. Tu pedido fue confirmado. Total: $27588.00", cuerpoEnviado());
        }

        @Test
        void elMailMuestraSiempreDosDecimales() {
            procesarSimple(REGULAR, unItem(10001), "CABA");

            assertEquals("Hola. Tu pedido fue confirmado. Total: $12101.15", cuerpoEnviado());
        }

        // Documenta el comportamiento ACTUAL, que es discutible: si el mail falla,
        // el error se traga en silencio y el pedido igual queda guardado.
        @Test
        void siElNotificadorFallaIgualSeGuardaElPedidoYSeDevuelveElTotal() {
            PedidoService servicio = new PedidoService(repositorio, new NotificadorQueFalla());

            double total = servicio.procesarPedido(EMAIL, REGULAR, casoRegular(), null, "CABA", false);

            assertEquals(6655.00, total, DELTA);
            assertEquals(1, repositorio.pedidosGuardados().size());
        }

        private String cuerpoEnviado() {
            return notificador.mailsEnviados().get(0).cuerpo();
        }
    }

    @Nested
    @DisplayName("Caso combinado")
    class CasoCombinado {

        @Test
        void premiumConMontoCuponExpressYEnvioGratis() {
            List<ItemPedido> items = List.of(
                    new ItemPedido("Notebook", 40000, 1),
                    new ItemPedido("Mouse", 2000, 2));

            double total = procesar(PREMIUM, items, "PROMO10", "Buenos Aires", true);

            assertEquals(42178.18, total, DELTA);
        }
    }
}
