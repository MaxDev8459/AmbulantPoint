# AmbulantPoint

Aplicación Android para vendedores ambulantes y emprendedores estudiantiles. Gestiona inventario offline, operaciones de venta diaria, conciliación de caja y reportes de negocio — sin depender de conexión a internet.

---

## Tabla de Contenidos

- [Descripción General](#descripción-general)
- [Tecnologías](#tecnologías)
- [Estructura del Proyecto](#estructura-del-proyecto)
- [Módulos del Sistema](#módulos-del-sistema)
- [Cómo ejecutar el proyecto](#cómo-ejecutar-el-proyecto)
- [Arquitectura](#arquitectura)

---

## Descripción General

AmbulantPoint es una solución móvil **100% offline** diseñada para vendedores de campo. Permite:

- Gestionar un catálogo de productos con categorías, precios y stock general.
- Operar una jornada de venta diaria con carga de producto, registro de ventas y mermas.
- Conciliar la caja al cierre del día.
- Visualizar métricas de desempeño desde un dashboard.
- Configurar métodos de pago habilitados (efectivo, tarjeta, transferencia).

---

## Tecnologías

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| Plataforma | Android (minSdk 24) |
| UI | ViewBinding + Material Components |
| Base de datos | SQLite nativo (sin Room) |
| Arquitectura | UI → Service → DAO → SQLite |
| Build | Gradle (Kotlin DSL) |

---

## Estructura del Proyecto

```
app/src/main/java/com/ambulantpoint/
│
├── ui/                          (Capa de presentación)
│   ├── catalogo/                (M1)
│   │   ├── GestionProductosActivity.kt
│   │   ├── GestionStockActivity.kt
│   │   └── ProductoFormActivity.kt
│   ├── venta/                   (M2)
│   │   ├── IniciarVentaActivity.kt
│   │   ├── VentaActivity.kt
│   │   └── MetodosPagoActivity.kt
│   ├── conciliacion/            (M3)
│   │   └── ReporteActivity.kt
│   └── dashboard/               (M4)
│       ├── DashboardActivity.kt
│       └── NotificacionesActivity.kt
│
├── service/                     (Capa de lógica de negocio)
│   ├── CatalogService.kt
│   ├── VentaService.kt
│   └── MetodoPagoInput.kt
│
└── data/
    ├── dao/                     (Capa de acceso a datos)
    │   ├── CategoriaDao.kt
    │   ├── ProductoDao.kt
    │   ├── VentaDao.kt
    │   ├── DetalleVentaDao.kt
    │   ├── JornadaVentaDao.kt
    │   ├── CargaDiariaDao.kt
    │   ├── MovimientoInventarioDao.kt
    │   ├── CorteCajaDao.kt
    │   └── MetodoPagoDao.kt
    ├── model/                   (Data classes de dominio)
    │   ├── Categoria.kt
    │   ├── Producto.kt
    │   ├── Venta.kt
    │   ├── DetalleVenta.kt
    │   ├── JornadaVenta.kt
    │   ├── CargaDiaria.kt
    │   ├── MovimientoInventario.kt
    │   └── CorteCaja.kt
    └── db/                      (Esquema SQLite)
        ├── DatabaseContract.kt
        └── DatabaseHelper.kt
```

---

## Módulos del Sistema

| Módulo | Estado | Descripción |
|---|---|---|
| **M1 — Gestión de Catálogo** | Completo | Alta, edición y eliminación de categorías y productos. Gestión de stock general. |
| **M2 — Operación de Venta Diaria** | En desarrollo | Inicio de jornada, carga diaria, registro de ventas, mermas y cierre. |
| **M3 — Cierre y Conciliación** | Pendiente | Reporte de jornada, corte de caja y conciliación de efectivo. |
| **M4 — Dashboard e Inteligencia** | Pendiente | Métricas de desempeño, notificaciones y predicciones de demanda. |

---

## Cómo ejecutar el proyecto

### Requisitos

- Android Studio Hedgehog o superior
- Android SDK 34
- JDK 17
- Dispositivo o emulador con Android 7.0+ (API 24)

### Pasos

```bash
# 1. Clonar el repositorio
git clone https://github.com/MaxDev8459/AmbulantPoint.git

# 2. Abrir en Android Studio
# File → Open → seleccionar la carpeta AmbulantPoint

# 3. Sincronizar Gradle
# Android Studio lo hace automáticamente al abrir

# 4. Ejecutar
# Run → Run 'app' (Shift+F10)
```

> La base de datos SQLite se crea automáticamente en el primer arranque de la app. No se requiere configuración adicional.

---

## Arquitectura

```
┌─────────────────────────────────────────────┐
│              UI (Activities)                 │
│  ui/catalogo/  ui/venta/  ui/conciliacion/  │
│  ui/dashboard/  MainActivity                │
└────────────────────┬────────────────────────┘
                     │ llama a
┌────────────────────▼────────────────────────┐
│           Service Layer                      │
│  CatalogService, VentaService               │
│  Valida reglas de negocio                   │
│  Lanza ValidationException /                │
│  BusinessRuleException                      │
└────────┬───────────────────┬────────────────┘
         │                   │
┌────────▼──────┐   ┌────────▼───────────────┐
│ CategoriaDao  │   │  ProductoDao, VentaDao  │
│ CorteCajaDao  │   │  DetalleVentaDao        │
│ MetodoPagoDao │   │  JornadaVentaDao        │
│               │   │  CargaDiariaDao         │
│               │   │  MovimientoInventarioDao│
└───────────────┘   └────────────────────────┘
         │                   │
┌────────▼───────────────────▼────────────────┐
│              SQLite (DatabaseHelper)         │
│  DatabaseContract — contrato de esquema     │
│  12 tablas: categoria, producto, venta,     │
│  detalle_venta, jornada_venta, carga_diaria,│
│  movimiento_inventario, corte_caja,         │
│  metodo_pago, efectivo, tarjeta,            │
│  transferencia                              │
└─────────────────────────────────────────────┘
```

**Decisiones de diseño clave:**

- **Soft-delete:** Productos y categorías con historial de ventas se marcan con `activo = 0`, nunca se eliminan fisicamente.
- **Snapshot de precio:** `detalle_venta.precio_unitario` guarda el precio al momento de la venta; cambios futuros de precio no lo afectan.
- **Class Table Inheritance (MetodoPago):** Tres tablas separadas (`efectivo`, `tarjeta`, `transferencia`) con PK compartida con tabla padre `metodo_pago`.
- **Sin Room:** SQLite nativo para control total del esquema y las transacciones.
- **Transaccionalidad:** Toda escritura en BD usa `beginTransaction` / `setTransactionSuccessful` / `endTransaction`.

---

*Desarrollado por Maximiliano Vasquez Arroyo — Proyecto Ingeniería de Software, enero-junio 2026*
