# CLAUDE.md — AmbulantPoint: Contexto Maestro para Claude Code

> **Versión:** 1.0 | **Fecha:** Abril 2026 | **Autor:** Vasquez Arroyo Maximiliano (Grupo XA)
> **Propósito:** Este archivo es la fuente de verdad para toda sesión de Claude Code en este proyecto. Léelo completo antes de generar, modificar o eliminar cualquier archivo.

---

## 1. IDENTIDAD Y ROL DE CLAUDE CODE EN ESTE PROYECTO

Asumes el rol de **Arquitecto de Sistemas y Desarrollador Senior Android (Kotlin)**. Tu nivel de exigencia técnica está al máximo:

- **Cero código especulativo:** No generes código que no tenga trazabilidad directa a un RF o CU documentado.
- **Auditoría primero:** Antes de modificar cualquier archivo existente, léelo y confirma su estado actual con el desarrollador.
- **Contratos de tipo son ley:** Los tipos `Int`/`Long` de cada entidad están fijados. No los cambies sin aprobación explícita.
- **Preguntar ante ambigüedad:** Si hay conflicto entre este archivo y el código existente, detente y pregunta antes de proceder.
- **Transaccionalidad siempre:** Toda operación que modifique la BD debe estar en un bloque de transacción con rollback explícito.

---

## 2. DESCRIPCIÓN DEL PROYECTO

**Nombre:** AmbulantPoint
**Naturaleza:** Aplicación móvil Android 100% offline para estudiantes emprendedores y vendedores ambulantes. Permite gestionar inventario dual, registrar ventas express, cerrar caja con conciliación automática y consultar inteligencia de negocios predictiva.

**Contexto académico:** Proyecto de Ingeniería de Software (enero–junio 2026, semestre activo). Metodología tradicional con documentación IEEE 830 y UML. Cada decisión de código debe ser trazable a un requisito funcional.

**Restricciones de negocio críticas:**
- Sin login ni multi-usuario. Un solo emprendedor por dispositivo.
- 100% offline. Cero llamadas de red. Cero Firebase, Retrofit, ni APIs externas.
- UI diseñada para operar bajo luz solar directa (alto contraste).
- Tiempo de respuesta para registrar una venta: ≤ 1 segundo (RNF-03).

---

## 3. STACK TECNOLÓGICO

| Componente | Tecnología | Notas |
|---|---|---|
| Lenguaje | **Kotlin** | Canónico. El PROMPT_MAESTRO menciona Java — está obsoleto. Usar Kotlin. |
| UI | Android Views + Material Components 1.x | No Jetpack Compose |
| BD Local | SQLite vía `DatabaseHelper` (SQLiteOpenHelper) | Sin Room, sin ORM |
| Binding | ViewBinding | Habilitado en Gradle. Siempre usar, nunca `findViewById` |
| IDE | Android Studio (proyecto principal) / VS Code + Claude Code (desarrollo asistido) |
| Paquete raíz | `com.ambulantpoint` | |
| minSdk | 24 | API 24 Android 7.0 |

---

## 4. ARQUITECTURA GENERAL

### 4.1 Módulos (orden de dependencia de datos — también es el orden de desarrollo)

| ID | Nombre | CUs | RFs | Estado |
|---|---|---|---|---|
| **M1** | Gestión de Catálogo | CU-10, CU-03 | RF-18 | ✅ **En desarrollo activo** |
| **M2** | Operación de Venta Diaria | CU-01, CU-02, CU-03, CU-04 | RF-01 a RF-09 | ⏳ Pendiente (espera M1 estable) |
| **M3** | Cierre y Conciliación | CU-05, CU-06 | RF-10 a RF-13 | ⏳ Pendiente |
| **M4** | Inteligencia de Negocios | CU-08, CU-09, CU-11 | RF-14 a RF-19 | ⏳ Pendiente |

> CU-07 está **RESERVADO** (fue dividido en CU-08 y CU-09). No crear ningún artefacto con ID CU-07.

### 4.2 Capas de arquitectura

```
PRESENTATION LAYER  →  Activities + Layouts + ViewBinding
        ↓
SERVICE LAYER       →  CatalogService, VentaService, etc. (lógica de negocio + validaciones)
        ↓
DAO LAYER           →  CategoriaDao, ProductoDao, etc. (acceso a SQLite)
        ↓
DATABASE LAYER      →  DatabaseHelper (SQLiteOpenHelper), DatabaseContract
```

**Paquetes:**
```
com.ambulantpoint/
├── data/
│   ├── db/          → DatabaseHelper, DatabaseContract
│   ├── dao/         → *Dao.kt
│   └── model/       → data classes (Categoria, Producto, Venta, etc.)
├── service/         → *Service.kt (lógica de negocio)
└── ui/
    ├── catalogo/    → M1 Activities
    ├── venta/       → M2 Activities
    ├── conciliacion/→ M3 Activities
    └── dashboard/   → M4 Activities
```

---

## 5. MODELO DE DATOS — CONTRATOS DE TIPOS (INMUTABLES)

### 5.1 Entidades y tipos canónicos

| Entidad | ID tipo | Notas clave |
|---|---|---|
| `Categoria` | `Int` | `CategoriaDao` usa `Int` para IDs |
| `Producto` | `Long` | `ProductoDao` usa `Long` para IDs; `categoriaId: Long` |
| `Venta` | `Long` | |
| `DetalleVenta` | `Long` | `precio_unitario` es snapshot inmutable capturado en el momento de venta |
| `JornadaVenta` | `Long` | Contiene `fondo_caja: Double` |
| `CargaDiaria` | `Long` | Contiene `merma: Int` (granularidad de pérdidas) |
| `MovimientoInventario` | `Long` | Contiene `precio_perdida: Double` (snapshot para mermas) |
| `CorteCaja` | `Long` | |
| `PrediccionDemanda` | `Long` | Clave UNIQUE compuesta |

### 5.2 Bridge de tipos (patrón establecido)

`CatalogService` usa `categoriaId: Int` en métodos de `Categoria` y llama a `ProductoDao` con `.toLong()` como puente. Este patrón ya está implementado — no romperlo.

```kotlin
// Patrón correcto establecido:
fun createProducto(nombre: String, categoriaId: Int, ...) {
    productoDao.insert(Producto(categoriaId = categoriaId.toLong(), ...))
}
```

### 5.3 Decisiones de BD locked

| Decisión | Descripción |
|---|---|
| `MetodoPago` — Class Table Inheritance | Tres tablas separadas: `efectivo`, `tarjeta`, `transferencia`. PK compartida con tabla padre `metodo_pago`. Afecta UML y toda la arquitectura. |
| Soft-delete | `activo: Boolean` en `Categoria` y `Producto`. Si hay historial de ventas → eliminación lógica. Si no hay historial → eliminación física permitida. |
| `ON UPDATE CASCADE` | En todas las FK del esquema. |
| `ON DELETE SET NULL` | Para referencias de auditoría nullable en `MovimientoInventario`. |
| `precio_unitario` inmutable | Capturado como snapshot en `DetalleVenta` al momento de venta. Nunca se recalcula. |
| `precio_perdida` en `MovimientoInventario` | Snapshot del precio al momento de la merma (D11). |
| `costo_base` eliminado | Removido de todos los archivos (D10). No agregar de vuelta. |
| `stockGeneral` / `stock_general` | Kotlin property: `stockGeneral` (camelCase). Columna BD: `stock_general` (snake_case). Ambos son canónicos en su capa. |
| UNIQUE compuesta en `PrediccionDemanda` | Constraint compuesto — verificar antes de insertar. |

---

## 6. REQUISITOS FUNCIONALES (RF-01 a RF-19)

| ID | Nombre | Módulo | CU(s) |
|---|---|---|---|
| RF-01 | Seleccionar productos del sistema | M2 | CU-01 |
| RF-02 | Registrar cantidad de fondo de caja | M2 | CU-01 |
| RF-03 | Generación automática de filtros Slash | M2 | CU-01 |
| RF-04 | Confirmar carga diaria | M2 | CU-01 |
| RF-05 | Filtrar por categoría activa (Slash Filter) | M2 | CU-02 |
| RF-06 | Registrar venta en ≤ 1 toque | M2 | CU-02 |
| RF-07 | Seleccionar método de pago por venta | M2 | CU-02 |
| RF-08 | Calcular comisión automática (Tarjeta/Transferencia) | M2 | CU-02 |
| RF-09 | Registrar merma por producto dañado | M2 | CU-04 |
| RF-10 | Confirmar cierre de jornada (prevenir cierre accidental) | M3 | CU-05 |
| RF-11 | Ingresar efectivo real contado | M3 | CU-05 |
| RF-12 | Generar reporte de cierre con mermas | M3 | CU-06 |
| RF-13 | Retorno automático de productos sobrantes a Stock General | M3 | CU-05 |
| RF-14 | Presentar estadísticas en Dashboard | M4 | CU-08 |
| RF-15 | Calcular promedio de venta esperado por producto | M4 | CU-09 |
| RF-16 | Evaluar Stock General vs. promedio esperado | M4 | CU-09 |
| RF-17 | Generar alerta de déficit de producción | M4 | CU-09 |
| RF-18 | Gestión de Catálogo de Productos y Categorías | M1 | CU-10 |
| RF-19 | Consultar Historial de Jornadas por fecha/rango | M4 | CU-11 |

---

## 7. CASOS DE USO (CU-01 a CU-11, CU-07 RESERVADO)

### CU-01: Cargar Venta Diaria (RF-01, RF-02, RF-03, RF-04)
**Precondición:** CU-10 ejecutado. Existen productos en Stock General > 0. No hay jornada activa.
**Flujo:** Usuario selecciona productos y cantidades → ingresa fondo de caja → confirma. Sistema reduce stock general, crea CargaDiaria, inicializa fondo, genera filtros Slash, crea JornadaVenta y bloquea acceso a stock general hasta cierre.
**Flujos alternos:** A1 stock insuficiente (ofrece máximo disponible), A2 fondo = $0 (advertencia, permite continuar).

### CU-02: Filtrar y Realizar Venta (RF-05, RF-06, RF-07, RF-08)
**Precondición:** Jornada activa. Hay productos en CargaDiaria > 0.
**Flujo:** Slash Filter muestra solo categorías con existencia. Un toque registra venta → selecciona método de pago → sistema calcula comisión si aplica → reduce stock diario → si categoría queda en 0, se desactiva del Slash Filter automáticamente.
**Extiende:** CU-04 (merma).

### CU-03: Configurar Métodos de Pago
**Precondición:** Acceso al módulo de catálogo.
**Flujo:** Configura comisión de Tarjeta (%), datos bancarios de Transferencia (CLABE 18 dígitos, banco). Persistido en tablas `tarjeta` y `transferencia`.

### CU-04: Registrar Merma
**Precondición:** Jornada activa. Producto en CargaDiaria > 0.
**Flujo:** Usuario registra producto dañado con cantidad. Sistema crea `MovimientoInventario` con `precio_perdida` snapshot, reduce CargaDiaria, actualiza totales de pérdida en JornadaVenta.

### CU-05: Finalizar / Cerrar Caja (RF-10, RF-11, RF-13)
**Precondición:** Jornada activa.
**Flujo:** Usuario confirma cierre (doble confirmación anti-accidental) → ingresa efectivo real contado → sistema calcula discrepancia automáticamente → retorna productos sobrantes a Stock General (RF-13) → cierra JornadaVenta → dispara CU-06.

### CU-06: Generar Reporte de Cierre (RF-12)
**Precondición:** CU-05 completado (se dispara automáticamente).
**Flujo:** Sistema genera reporte con ventas totales, desglose por método de pago, total de mermas con impacto financiero, discrepancia de caja.

### CU-08: Dashboard de Estadísticas (RF-14)
**Precondición:** Al menos 1 jornada cerrada.
**Flujo:** Muestra productos más/menos vendidos, ventas por hora, ingresos por método de pago. Sin tabla dedicada adicional — lee de `Venta`, `DetalleVenta`, `CorteCaja`.

### CU-09: Predicción de Demanda (RF-15, RF-16, RF-17)
**Precondición:** Al menos 7 jornadas cerradas (mínimo para predicción). Si < 7 → mostrar "Datos insuficientes".
**Flujo:** Calcula promedio de venta por producto por día de semana → compara con Stock General → genera alertas de déficit. Usa `PrediccionDemanda` con UNIQUE compuesta.

### CU-10: Gestionar Catálogo de Productos (RF-18)
**Precondición:** Para crear producto: existe al menos 1 categoría.
**Flujo CRUD completo con validaciones:**
- Nombre no duplicado
- precio > 0, costo ≥ 0 (nota: `costo_base` eliminado — D10)
- Eliminar con historial → soft-delete (activo=false)
- Eliminar categoría con productos → sistema impide hasta reasignar
- Editar precio durante jornada activa → aplica solo a ventas futuras (precio_unitario en DetalleVenta ya inmutable)

### CU-11: Consultar Historial de Jornadas (RF-19)
**Precondición:** Al menos 1 jornada cerrada.
**Flujo:** Filtrar por fecha individual o rango → mostrar reporte de cierre original. Datos provienen de `JornadaVenta`, `CorteCaja`, `Venta`.

---

## 8. MODELO DE CLASES UML (12 clases)

```
[AZUL - Usuario]
  Emprendedor

[VERDE - Dominio]
  Categoria        → id: Int, nombre: String, estadoActivoFiltro: Boolean
  Producto         → id: Long, nombre: String, categoriaId: Long, precioVenta: Double, stock_general: Int, activo: Boolean
  Venta            → id: Long, fechaHoraTransaccion: DateTime, totalCobrado: Double, comisionAplicada: Double
  DetalleVenta     → id: Long, idVenta: Long, cantidadVendida: Int, precioUnitarioAlmomento: Double, subtotal: Double
  MetodoPago       → id: Int, idMetodo: Int, nombreMetodo: String, requiereValidacionExterna: Boolean
    ├── Efectivo       (herencia)
    ├── Tarjeta        (herencia) → comisionTarjeta: Double, datosClaveTarjeta: String
    └── Transferencia  (herencia) → datosClaveTransferencia: String

[AMARILLO - Control]
  ConfiguracionApp → Singleton. porcentajeComisionTarjeta: Double, datosClaveTransferencia: String, bancoTitilar: String, usaTransferencia: Boolean
  JornadaVenta     → id: Long, nJornada: Int, fechaInicioJornada: DateTime, fechaHoraCierre: DateTime, fondoCapitalInicial: Double

[MORADO - Procesos/Reportes]
  CargaDiaria      → idCarga: Int, cantidadCargada: Int, cantidadVendida: Int, cantidadMerma: Int, cantidadSobrante: Int
  MovimientoInventario → idMovimiento: Int, tipoMovimiento: String, cantidad: Int, fechaHora: DateTime, motivoAjusteRazon: String, precio_perdida: Double
  CorteCaja        → idCorte: Int, efectivoEsperado: Double, efectivoRealContado: Double, discrepanciaEfectivo: Double, totalPerdidaMermas: Double
  GestorDashboard  → fechaInicioPrimFiltro: Date, fechaFinalFiltro: Date, listTopProductos: List<String>
  ModeloPredictivo → diasHistorialAAnalizar: Int, umbralAlertaStock: Int
```

**Relación clave:** JornadaVenta → CargaDiaria es **Composición** (no herencia). La CargaDiaria no existe sin una JornadaVenta activa.

---

## 9. ESTADO ACTUAL DE DESARROLLO — M1 (GESTIÓN DE CATÁLOGO)

### 9.1 Archivos implementados (Kotlin)

> ⚠️ REGLA CRÍTICA: Antes de modificar cualquiera de estos archivos, léelos del disco. No asumas que el contenido es idéntico a lo que está en este documento.

| Archivo | Paquete / Ruta | Estado |
|---|---|---|
| `DatabaseContract.kt` | `com.ambulantpoint.data.db` | ✅ Implementado, D10/D11 compliant |
| `DatabaseHelper.kt` | `com.ambulantpoint.data.db` | ✅ Implementado |
| `Categoria.kt` | `com.ambulantpoint.data.model` | ✅ `id: Int` |
| `Producto.kt` | `com.ambulantpoint.data.model` | ✅ `id: Long`, `categoriaId: Long`, sin `costo_base` |
| `CategoriaDao.kt` | `com.ambulantpoint.data.dao` | ✅ IDs como `Int` |
| `ProductoDao.kt` | `com.ambulantpoint.data.dao` | ✅ IDs como `Long`, incluye `getProductosTodosActivos()` |
| `CatalogService.kt` | `com.ambulantpoint.service` | ✅ Bridge `.toLong()` implementado |
| `MainActivity.kt` | `com.ambulantpoint.ui` | ✅ Menú principal con navegación a 6 Activities |
| `GestionProductosActivity.kt` | `com.ambulantpoint.ui.catalogo` | ✅ RecyclerView productos activos, FAB creación, edit/delete inline |
| `ProductoFormActivity.kt` | `com.ambulantpoint.ui.catalogo` | ✅ Creación + edición. Stock oculto en modo edición. `EXTRA_PRODUCTO_ID` |
| 4 Activities restantes | `com.ambulantpoint.ui.catalogo` | ⚠️ Esqueletos navegables (no implementados) |
| `colors.xml` | `res/values` | ✅ |
| `themes.xml` | `res/values` | ✅ |
| `dimens.xml` | `res/values` | ✅ |
| `AndroidManifest.xml` | raíz | ✅ Actualizado con todas las Activities |

### 9.2 Contratos de tipo — Regla de oro

```kotlin
// Categoria
data class Categoria(val id: Int, ...)

// CategoriaDao — usa Int
fun getCategoriaById(id: Int): Categoria?
fun insertCategoria(categoria: Categoria): Long

// Producto
data class Producto(val id: Long, val categoriaId: Long, ...)

// ProductoDao — usa Long
fun getProductoById(id: Long): Producto?
fun insertProducto(producto: Producto): Long

// CatalogService — bridge Int→Long
fun createProducto(nombre: String, categoriaId: Int, ...) {
    productoDao.insertProducto(Producto(categoriaId = categoriaId.toLong(), ...))
}
```

---

## 10. DECISIONES ARQUITECTÓNICAS CERRADAS (Pre-M2)

Resueltas el 2026-04-27. No reabrir sin aprobación explícita del desarrollador.

| ID | Decisión | Resultado |
|---|---|---|
| D-R1 | Nombre canónico de stock en `Producto` | `stockGeneral` en Kotlin, `stock_general` en columna BD. Ambos bloqueados. |
| D-R2 | `imagen_path` en `Producto` | **Excluido completamente.** Sin RF de soporte. No agregar en ninguna capa. |
| D-R3 | MetodoPago — Class Table Inheritance | Confirmado: 3 tablas separadas (`efectivo`, `tarjeta`, `transferencia`) con PK compartida. |
| D-R4 | CU-11 fuentes de datos | Suficiente con `JornadaVenta`, `CorteCaja` y `Venta`. Sin tablas adicionales. |
| D-R5 | Referencias en `MovimientoInventario` | `ref_venta_id` y `ref_carga_id` **separados** (mutuamente excluyentes, no unificar). |

---

## 11. REQUISITOS NO FUNCIONALES

| ID | Nombre | Descripción |
|---|---|---|
| RNF-01 | Offline total | Sin conexión a internet. Cero dependencias de red. |
| RNF-02 | Alto Contraste | UI legible bajo luz solar directa. Esquema claro/oscuro. |
| RNF-03 | Rendimiento en Venta | Registrar venta + actualizar pantalla en ≤ 1 segundo. |
| RNF-04 | Persistencia Transaccional | Commit a SQLite inmediato post-operación. Sin pérdida de datos por cierre inesperado. |
| RNF-05 | Escalabilidad | Soportar ≥ 1 año de registros. Reportes en ≤ 3 segundos. |

---

## 12. CONVENCIONES DE NOMENCLATURA

| Ámbito | Convención | Ejemplo |
|---|---|---|
| Paquetes | lowercase | `com.ambulantpoint.data.dao` |
| Clases / Interfaces | PascalCase | `CategoriaDao`, `ProductoFormActivity` |
| Funciones / Variables | camelCase | `getProductoById`, `fondoCaja` |
| Constantes | UPPER_SNAKE_CASE | `TABLE_PRODUCTO`, `EXTRA_PRODUCTO_ID` |
| Tablas BD (SQLite) | snake_case | `categoria`, `producto`, `jornada_venta` |
| Columnas BD | snake_case | `stock_general`, `precio_unitario`, `activo` |
| Layouts XML | módulo_descripción | `activity_gestion_productos.xml` |
| IDs en XML | tipo_descripción | `btn_guardar`, `rv_productos`, `fab_agregar` |

---

## 13. PATRONES Y PRINCIPIOS OBLIGATORIOS

### Transaccionalidad en SQLite
```kotlin
// SIEMPRE usar este patrón para operaciones de escritura
db.beginTransaction()
try {
    // operaciones
    db.setTransactionSuccessful()
} finally {
    db.endTransaction()
}
```

### ViewBinding
```kotlin
// SIEMPRE usar ViewBinding. Nunca findViewById.
private lateinit var binding: ActivityGestionProductosBinding

override fun onCreate(...) {
    binding = ActivityGestionProductosBinding.inflate(layoutInflater)
    setContentView(binding.root)
}
```

### Soft-Delete
```kotlin
// Nunca DELETE físico si el registro tiene historial de ventas
// Usar: UPDATE producto SET activo = 0 WHERE id = ?
```

### Sin pago mixto
Un único método de pago por venta. Los RFs no contemplan pago mixto. No implementar.

---

## 14. ARCHIVOS DE REFERENCIA EN EL PROYECTO

| Archivo | Descripción |
|---|---|
| `AmbulantPoint_RF_CU_Definitivo_v2.xlsx` | Fuente canónica de RFs y CUs. Usar sobre cualquier versión anterior. |
| `Analisis_Tablas_AmbulantPoint_v2.docx` | Especificación de tablas BD y flujos de CUs. |
| `DisenoInterfaces_4Modulos.docx` | Diseño de interfaces por módulo. |
| `AmbulantPoint_Demo.jsx` | UI dark theme — BASE para todas las pantallas post-exposición. |
| `DIAGRAMA_DE_CLASES.pdf` | Diagrama UML de clases con las 12 entidades. |
| `PlantillaCURF_AmbulantPoint_v2.docx` | Plantillas de casos de uso con trazabilidad. |
| `diseño_y_arquitectura.pdf` | Documento de arquitectura del sistema. |

> ⚠️ **`Reporte.xlsx` está OBSOLETO** — usa RF001–RF017 con 7 CUs. Ignorar en favor de `AmbulantPoint_RF_CU_Definitivo_v2.xlsx`.

---

## 15. DISEÑO DE UI — REGLAS DE REFERENCIA

- **Tema de desarrollo activo (post-exposición):** Dark theme definido en `AmbulantPoint_Demo.jsx`. Este es el tema base para M2 en adelante.
- **Tema de exposición académica:** Light theme (ya implementado en M1).
- **No usar `GridLayout` nativo** con `app:layout_columnWeight` — usar `LinearLayout` anidados con `android:layout_weight`.
- **ViewBinding:** Todo layout debe tener `android:id` en el elemento raíz.
- **FAB:** Solo icono (sin texto) en listas principales.
- El stock se oculta en modo edición de `ProductoFormActivity` (ya implementado — no revertir).

---

## 16. EDGE CASES ARQUITECTÓNICOS CONOCIDOS

| Escenario | Comportamiento Esperado |
|---|---|
| Último producto de una categoría se vende/merma | La categoría se desactiva del Slash Filter automáticamente (CU-02) |
| Predicción con < 7 jornadas cerradas | Mostrar "Datos insuficientes" — no calcular |
| App se cierra durante una venta | RNF-04: commit inmediato post-confirmación. La venta registrada persiste. |
| Edición de precio durante jornada activa | Se aplica solo a ventas futuras. `precio_unitario` en `DetalleVenta` ya existentes no cambia. |
| Eliminar categoría con productos asignados | Bloquear hasta que todos los productos sean reasignados a otra categoría |
| `PrediccionDemanda` duplicada | UNIQUE compuesta previene inserción. Hacer UPDATE si ya existe. |



## Approach
- Read existing files before writing. Don't re-read unless changed.
- Thorough in reasoning, concise in output.
- Skip files over 100KB unless required.
- No sycophantic openers or closing fluff.
- No emojis or em-dashes.
- Do not guess APIs, versions, flags, commit SHAs, or package names. Verify by reading code or docs before asserting.

---

*Fin del archivo CLAUDE.md — AmbulantPoint v1.0*
