# AmbulantPoint

Aplicación Android para vendedores ambulantes y emprendedores estudiantiles. Gestiona inventario offline, operaciones de venta diaria, conciliación de caja y reportes de negocio — sin depender de conexión a internet.

---

## Tabla de Contenidos

- [Descripción General](#descripción-general)
- [Tecnologías](#tecnologías)
- [Estructura del Proyecto](#estructura-del-proyecto)
  - [Frontend — Activities (UI)](#frontend--activities-ui)
  - [Backend — Services y DAOs (Lógica de Negocio)](#backend--services-y-daos-lógica-de-negocio)
  - [Database — Contrato y Helper (Persistencia)](#database--contrato-y-helper-persistencia)
- [Módulos del Sistema](#módulos-del-sistema)
- [Cómo ejecutar el proyecto](#cómo-ejecutar-el-proyecto)
- [Arquitectura](#arquitectura)

---

## Descripción General

AmbulantPoint es una solución móvil **100% offline** diseñada para vendedores de campo. Permite:

- Gestionar un catálogo de productos con categorías, precios y stock.
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
| UI | ViewBinding + AppCompat |
| Base de datos | SQLite nativo (sin Room) |
| Arquitectura | Capas: UI → Service → DAO → SQLite |
| Build | Gradle (Kotlin DSL) |

---

## Estructura del Proyecto

```
app/src/main/java/com/ambulantpoint/
│
├── 📱 FRONTEND (Activities — Capa de UI)
│   ├── MainActivity.kt
│   ├── DashboardActivity.kt
│   ├── IniciarVentaActivity.kt
│   ├── NotificacionesActivity.kt
│   ├── ReporteActivity.kt
│   ├── MetodosPagoActivity.kt
│   ├── GestionProductosActivity.kt
│   └── Productoformactivity.kt
│
├── ⚙️ BACKEND (Services y DAOs — Lógica de Negocio y Acceso a Datos)
│   ├── service/
│   │   └── CatalogService.kt
│   └── data/
│       ├── dao/
│       │   ├── CategoriaDao.kt
│       │   └── ProductoDao.kt
│       └── model/
│           ├── Categoria.kt
│           └── producto.kt
│
└── 🗄️ DATABASE (Contrato y Helper — Esquema SQLite)
    └── data/db/
        ├── DatabaseContract.kt
        └── DatabaseHelper.kt
```

---

### Frontend — Activities (UI)

Ubicación: `app/src/main/java/com/ambulantpoint/`

Las Activities son la capa de presentación. Muestran datos al usuario y delegan toda la lógica al `CatalogService`. No contienen reglas de negocio.

| Archivo | Descripción |
|---|---|
| `MainActivity.kt` | Pantalla principal con el menú de navegación a todos los módulos |
| `GestionProductosActivity.kt` | Lista todos los productos activos con opciones de editar y eliminar |
| `Productoformactivity.kt` | Formulario para crear o editar un producto (modo creación / modo edición) |
| `MetodosPagoActivity.kt` | Configuración de métodos de pago activos con regla mínimo 1 activo |
| `IniciarVentaActivity.kt` | *(Esqueleto M2)* Pantalla de inicio de jornada de venta |
| `DashboardActivity.kt` | *(Esqueleto M4)* Dashboard de métricas del negocio |
| `ReporteActivity.kt` | *(Esqueleto M3)* Generación de reportes de cierre |
| `NotificacionesActivity.kt` | *(Esqueleto M4)* Notificaciones y predicciones |

---

### Backend — Services y DAOs (Lógica de Negocio)

Ubicación: `app/src/main/java/com/ambulantpoint/service/` y `app/src/main/java/com/ambulantpoint/data/`

#### Service Layer

| Archivo | Descripción |
|---|---|
| `CatalogService.kt` | Único punto de entrada para operaciones de catálogo. Orquesta CategoriaDao y ProductoDao. Lanza `ValidationException` y `BusinessRuleException` ante datos o reglas inválidas. |

#### DAO Layer

| Archivo | Descripción |
|---|---|
| `CategoriaDao.kt` | CRUD completo de categorías. Soft-delete (activo=0) y hard-delete según historial. |
| `ProductoDao.kt` | CRUD de productos con gestión atómica de stock (incrementar / decrementar). |

#### Models

| Archivo | Descripción |
|---|---|
| `Categoria.kt` | Data class de dominio para categorías. Sin dependencias de Android. |
| `producto.kt` | Data class de dominio para productos. Incluye `tieneStockDisponible()`. |

---

### Database — Contrato y Helper (Persistencia)

Ubicación: `app/src/main/java/com/ambulantpoint/data/db/`

| Archivo | Descripción |
|---|---|
| `DatabaseContract.kt` | Define nombres de tablas y columnas como constantes. Ningún archivo externo debe usar strings literales de columnas — siempre usar estas constantes. |
| `DatabaseHelper.kt` | Singleton `SQLiteOpenHelper`. Crea las tablas en `onCreate()` respetando el orden de FKs y las destruye/recrea en `onUpgrade()`. Activa `PRAGMA foreign_keys = ON` en cada conexión. |

#### Tablas definidas en `DatabaseContract`

| Tabla | Módulo | Descripción |
|---|---|---|
| `categoria` | M1 | Categorías del catálogo con soft-delete |
| `producto` | M1 | Productos con precio, stock y FK a categoría |
| `metodo_pago` | M2 | Tabla padre CTI para métodos de pago |
| `efectivo` | M2 | Hija CTI — pagos en efectivo |
| `tarjeta` | M2 | Hija CTI — pagos con tarjeta (con comisión) |
| `transferencia` | M2 | Hija CTI — transferencias bancarias |
| `jornada_venta` | M2 | Sesión de trabajo diaria del vendedor |
| `carga_diaria` | M2 | Productos cargados por jornada |
| `venta` | M2 | Cabecera de cada venta realizada |
| `detalle_venta` | M2 | Líneas de venta con snapshot de precio (D8) |
| `movimiento_inventario` | M1/M2 | Trazabilidad completa de stock |
| `corte_caja` | M3 | Resumen de cierre y conciliación de caja |

---

## Módulos del Sistema

| Módulo | Estado | Descripción |
|---|---|---|
| **M1 — Gestión de Catálogo** | ✅ Completo | Alta, edición y eliminación de categorías y productos. Gestión de stock general. |
| **M2 — Operación de Venta Diaria** | 🔄 En desarrollo | Inicio de jornada, carga diaria, registro de ventas, mermas y cierre. |
| **M3 — Cierre y Conciliación** | 🔄 Pendiente | Reporte de jornada, corte de caja y conciliación de efectivo. |
| **M4 — Dashboard e Inteligencia** | 🔄 Pendiente | Métricas de desempeño, notificaciones y predicciones de demanda. |

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
┌─────────────────────────────────────────┐
│           FRONTEND (Activities)          │
│  MainActivity, GestionProductosActivity  │
│  ProductoFormActivity, MetodosPago...    │
└──────────────┬──────────────────────────┘
               │ llama a
┌──────────────▼──────────────────────────┐
│         BACKEND (CatalogService)         │
│  Valida reglas de negocio               │
│  Lanza ValidationException /            │
│  BusinessRuleException                  │
└──────────┬──────────────┬───────────────┘
           │              │
┌──────────▼───┐  ┌───────▼──────────────┐
│ CategoriaDao │  │     ProductoDao       │
│ (SQLite CRUD)│  │ (SQLite CRUD + Stock) │
└──────────────┘  └──────────────────────┘
           │              │
┌──────────▼──────────────▼───────────────┐
│           DATABASE (SQLite)              │
│  DatabaseHelper (Singleton)             │
│  DatabaseContract (Contrato de esquema) │
└─────────────────────────────────────────┘
```

**Decisiones de diseño clave:**

- **Soft-delete (D3):** Los productos y categorías con historial de ventas nunca se eliminan físicamente — se marcan con `activo = 0`.
- **Snapshot de precio (D8):** `detalle_venta.precio_unitario` guarda el precio al momento de la venta, independiente de cambios futuros.
- **Sin Room:** Se usa SQLite nativo para control total del esquema y las transacciones.
- **Sin ViewModel/LiveData en M1:** Lógica directa en Activity según alcance del módulo.
- **SharedPreferences para métodos de pago:** Configuración global de 3 booleanos — SQLite sería sobrediseño.

---

*Desarrollado por Maximiliano Vasquez Arroyo*
