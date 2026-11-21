# demo-calidad: refactoring en vivo (Calidad de Software, SIP)

Repo chico para una demo de 15 minutos: un ejemplo de refactoring resuelto (5 min) y uno con `TODO` que se escribe entre todos (10 min).

- Dominio: procesamiento de pedidos de una tienda online.
- Java 17+, Maven, JUnit 5. Sin Spring, sin base de datos, sin Mockito (los dobles de prueba son clases a mano).
- `PedidoService` tiene smells plantados a propósito. `PedidoServiceTest` es la red de seguridad: si los tests pasan después de un refactor, el comportamiento se conservó.
- Se usa `double` a propósito (no `BigDecimal`), para que el código se parezca a lo que escriben los alumnos.

## Cómo correr

```bash
mvn test                                  # tests (tienen que pasar en todos los tags)
mvn -Pcalidad verify                      # cobertura con JaCoCo (reporte en target/site/jacoco)
mvn -Pcalidad test org.pitest:pitest-maven:mutationCoverage   # mutación con PIT (reporte en target/pit-reports)
```

## Reglas de negocio

Firma: `double procesarPedido(String email, int tipoCliente, List<ItemPedido> items, String cupon, String provincia, boolean envioExpress)`. Constructor: `PedidoService(RepositorioPedidos, NotificadorEmail)`.

**Validaciones, en este orden (se lanza la excepción en cuanto falla una):**

| # | Condición | Excepción y mensaje |
|---|---|---|
| 1 | `email` nulo o sin `@` | `IllegalArgumentException("Email inválido")` |
| 2 | `items` nulo o vacío | `IllegalArgumentException("El pedido no tiene items")` |
| 3 | algún ítem con `cantidad <= 0` | `IllegalArgumentException("Cantidad inválida")` |
| 4 | suma de cantidades mayor a 50 | `IllegalStateException("Límite de 50 unidades por pedido")` |
| 5 | `tipoCliente` distinto de 1, 2 o 3 | `IllegalArgumentException("Tipo de cliente desconocido")` |

**Cálculo del total:**

1. `subtotal` = suma de `precioUnitario * cantidad`.
2. Tasa por tipo de cliente: 1 (regular) = 0%, 2 (premium) = 10%, 3 (mayorista) = 20%. `base = subtotal - subtotal * tasa`.
3. Si `base > 10000` (estrictamente mayor): `base = base - base * 0.05`.
4. Cupón (comparación exacta con `equals`, sensible a mayúsculas; solo se aplica uno):
   - `"PROMO10"`: `base = base - base * 0.10`.
   - `"BIENVENIDA"`: `base = max(0, base - 500)`.
   - `"SIN_ENVIO"`: marca envío gratis.
   - Nulo, vacío o desconocido: se ignora en silencio.
5. Envío base según provincia: `"CABA"` = 500, `"Buenos Aires"` = 800, cualquier otra cosa o `null` = 1500. Si el cupón fue `SIN_ENVIO` o `base >= 20000` (con la base ya descontada), el envío base es 0.
6. Si `envioExpress`, se suman 1000 al envío, siempre (incluso con envío gratis).
7. `total = round2((base + envio) * 1.21)`, con `round2(x) = Math.round(x * 100) / 100.0`.

**Efectos (después de calcular el total, nunca si falló una validación):**

1. `repositorio.guardar(new Pedido(email, items, total))`.
2. `notificador.enviar(email, "Pedido confirmado", cuerpo)`, con `cuerpo = saludo + ". Tu pedido fue confirmado. Total: $" + total con dos decimales` (`Locale.US`). Saludo: tipo 1 = `Hola`, tipo 2 = `Hola cliente premium`, tipo 3 = `Hola cliente mayorista`.
3. Si el envío del mail lanza una excepción, **se ignora** (el pedido ya quedó guardado y se devuelve el total). Es un comportamiento discutible que los tests dejan fijado.
4. Devuelve `total`.

## Smells plantados en `v0-antes`

| Smell | Dónde | Refactor |
|---|---|---|
| Long Method | todo `procesarPedido` | Extract Method |
| Lista larga de parámetros (6) | la firma | Introduce Parameter Object (solo comentado) |
| Ifs anidados | validaciones 1 a 3, con `else throw` al final | Guard clauses |
| Números mágicos | `50`, `10000`, `20000`, `0.05`, `0.10`, `0.20`, `500`, `800`, `1500`, `1000`, `1.21`, `1`/`2`/`3` | Constantes con nombre |
| Obsesión por primitivos | `tipoCliente` int; `cupon` y `provincia` String | Enum / clase de valor (solo mencionado) |
| Código duplicado | tarifa por provincia (rama express y no express); cuerpo del mail (una rama por tipo) | Extract Method |
| Feature Envy | el bucle del subtotal usa `precioUnitario()` y `cantidad()` del ítem | Move Method |
| Lógica de descuento mezclada | descuento por tipo y por monto dentro del método | Extract Class (`PoliticaDeDescuento`) |
| `catch (Exception e) {}` vacío | alrededor del envío del mail | Loguear o decidir |
| Límite fijo sin plan B | las 50 unidades como literal | Constante con nombre (eco del caso Cloudflare) |
| Código muerto | `calcularRecargoViejo()`, bloque comentado, variable local sin usar | Eliminar |

## Tags

| Tag | Estado |
|---|---|
| `v0-antes` | código con smells, tests en verde |
| `v1-ejemplo-resuelto` | ejemplo 1 en tres commits: constantes, guard clauses, Extract Method |
| `v2-todo` | `v1` + `TODO` sobre el cálculo del descuento. Punto de partida del ejemplo 2 |
| `v3-final` | ejemplo 2 hecho: `PoliticaDeDescuento`, un commit por paso |

En todos los tags `mvn test` pasa con la misma clase de tests.

## Guion de la demo (15 minutos)

1. **Ejemplo resuelto (5 min).** `git diff v0-antes v1-ejemplo-resuelto` commit por commit (`git log --oneline v0-antes..v1-ejemplo-resuelto`). Pregunta: ¿cómo sabemos que el comportamiento no cambió? Correr `mvn test` en cada commit.
2. **Ejemplo con TODO (10 min).** Partir de `git checkout v2-todo`, leer el `TODO`. Los alumnos dictan los pasos (crear la clase, mover el cálculo, probar) y se corren los tests entre cada uno. Si algo sale mal, saltar a `v3-final`.
3. **Cierre (si sobra tiempo).** Buscar un smell más en lo que quedó. Candidatos: el `catch` vacío, el límite de 50 unidades como literal, `tipoCliente` como `int`. Conectar con el caso Cloudflare (límite fijo sin plan B, error sin manejar).
4. Opcional: pasar SonarLint o SonarCloud sobre `v0-antes` y mostrar el reporte.

## Confianza en los tests

- Cobertura (`-Pcalidad`): 100% de ramas de `PedidoService` en `v0-antes`. Las líneas llegan a 77 de 78: la que falta es el método muerto `calcularRecargoViejo()`, que nadie llama y no se puede cubrir (es parte del smell).
- Mutación (PIT): sobreviven solo los mutantes del código muerto. Ninguno en comparaciones ni constantes de las reglas.
