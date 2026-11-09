# RiTech Exports — Backend

API REST del sistema de gestión de exportaciones de café y cacao hacia la Unión Europea:
trazabilidad EUDR, control de calidad, cumplimiento normativo, órdenes y logística.

- Java 17 · Spring Boot 3.5 · Spring Data JPA · Spring Security (JWT) · MySQL 8.0.16+
- Documentación interactiva (Swagger): `http://localhost:8080/docs`

## Cómo está pensado

La base de datos (`database/db_ritech_exports.sql`) ya contiene buena parte de las reglas de negocio:
triggers, CHECK, el motor de validación `sp_validar_lote` y las vistas. El backend **no las repite ni
las reemplaza**:

- Hibernate nunca crea ni modifica tablas (`ddl-auto: none`). El esquema es el del script.
- Los mensajes de los triggers (`SIGNAL 45000`) llegan al usuario tal cual, como HTTP 422.
  Los CHECK violados dan 400, los duplicados 409 y los borrados con hijos 409.
- Los datos derivados (`estado_lote`, `valor_total`) los mantiene la base. Las entidades los
  leen, pero nunca los escriben.
- **Auditoría:** antes de cada operación de escritura el backend ejecuta
  `SET @app_id_usuario, @app_ip_origen` sobre la misma conexión (`SesionAuditoriaAspecto`). Así los
  triggers `trg_aud_*` registran quién hizo el cambio. Al terminar las limpia, para que una conexión
  reutilizada del pool no herede el autor de otra petición.

## Ejecutar en local

1. **Crear la base.** En MySQL Workbench: *File → Open SQL Script…*, abrir `database/db_ritech_exports.sql`
   y ejecutarlo completo (rayo ⚡). Crea `db_ritech_exports` con datos semilla y un lote de demostración.
2. **Arrancar el backend** (en la carpeta del proyecto):
   ```bash
   # Windows PowerShell
   $env:DB_PASSWORD="tu-clave-de-mysql"; ./mvnw spring-boot:run
   ```
   El perfil `local` se activa solo. Al primer arranque reemplaza los `password_hash` marcadores del
   script por la contraseña de `application-local.yml` (`ritech.semilla.password-inicial`).
3. Abrir `http://localhost:8080/docs`, llamar a `POST /api/auth/login` y pegar el token en **Authorize**.

Usuarios semilla: `admin@ritech.co`, `gestor@ritech.co`, `inspector@ritech.co`, `cliente@importadora-eu.com`.

### Variables de entorno

| Variable | Para qué | Por defecto |
|---|---|---|
| `DB_URL` | URL JDBC de MySQL | `jdbc:mysql://localhost:3306/db_ritech_exports…` |
| `DB_USER` / `DB_PASSWORD` | Credenciales de MySQL | `root` / vacía |
| `JWT_SECRET` | Firma de los tokens (mín. 32 caracteres) | obligatoria fuera del perfil local |
| `JWT_HORAS` | Vigencia del token | `8` |
| `SEED_PASSWORD` | Contraseña inicial de los usuarios semilla | vacía (no toca nada) |
| `ALLOWED_ORIGINS` | Orígenes CORS del frontend, separados por coma | `http://localhost:5173,http://localhost:3000` |
| `STORAGE_DIR` | Carpeta de soportes de certificados | `./storage` |

## Pruebas

```bash
./mvnw test
```
No necesitan MySQL. Comprueban que todas las entidades y consultas JPQL compilan, que la aplicación
arranca con la seguridad activa, la traducción de errores SQL, el JWT y el aspecto de auditoría.
La lógica de los triggers y procedimientos se prueba con los casos CP-0xx del propio script.

## Endpoints por historia de usuario

| Historia | Endpoints |
|---|---|
| SCRUM-8 Iniciar sesión | `POST /api/auth/login`, `GET /api/auth/yo` |
| SCRUM-9 Usuarios y roles | `GET/POST /api/usuarios`, `PUT /api/usuarios/{id}`, `PATCH /api/usuarios/{id}/estado`, `GET /api/roles` |
| SCRUM-10 Auditoría | `GET /api/auditoria?tabla=&idUsuario=&desde=&hasta=` |
| SCRUM-11/12 Clientes UE | `GET /api/paises?ue=true`, `GET/POST /api/clientes`, `PUT /api/clientes/{id}`, filtros `idPais`, `razonSocial`, `estado` |
| SCRUM-13 Productos | `GET/POST /api/productos`, `PUT/DELETE /api/productos/{id}` |
| SCRUM-14 Productores y fincas | `GET/POST /api/productores`, `GET/POST /api/productores/{id}/fincas`, `PUT /api/fincas/{id}` |
| SCRUM-15 Lotes | `GET/POST /api/lotes`, `GET/PUT /api/lotes/{id}` |
| SCRUM-16 Fincas de origen | `GET/POST /api/lotes/{id}/fincas`, `PUT/DELETE /api/lotes/{id}/fincas/{idFinca}`, `POST /api/lotes/{id}/cerrar-trazabilidad` |
| SCRUM-17/18 Inspección | `GET/POST /api/lotes/{id}/inspecciones` |
| SCRUM-19 Trazabilidad | `GET /api/lotes/{id}/trazabilidad` (el cliente solo ve sus lotes) |
| SCRUM-20 Certificados | `GET /api/certificaciones`, `GET/POST /api/lotes/{id}/certificados`, `POST/GET /api/lotes/{id}/certificados/{idCert}/soporte` |
| SCRUM-21 Reglas UE | `GET/POST /api/reglas`, `PUT /api/reglas/{id}`, `PATCH /api/reglas/{id}/activa?valor=`, `GET /api/reglas/parametros` |
| SCRUM-22 Validación | `POST /api/lotes/{id}/validar`, `GET /api/lotes/{id}/dictamen` |
| SCRUM-23 Alertas | `GET /api/certificados/por-vencer?dias=30` + tarea diaria que marca `VENCIDO` |
| SCRUM-25 Órdenes | `GET/POST /api/ordenes`, `GET/PUT /api/ordenes/{id}`, `PATCH /api/ordenes/{id}/estado` |
| SCRUM-26 Asignar lotes | `POST /api/ordenes/{id}/lotes`, `PUT/DELETE /api/ordenes/{id}/lotes/{idLote}` |
| SCRUM-28 Programar envío | `GET/POST /api/transportistas`, `POST /api/ordenes/{id}/envios` |
| SCRUM-29/30 Seguimiento | `POST /api/envios/{id}/seguimiento`, `GET /api/envios`, `GET /api/envios/{id}` |

**Pendientes, de los Sprints 3 y 4:** SCRUM-24 (Declaración DDS EUDR), SCRUM-27 (factura y lista de empaque
en PDF), SCRUM-31 (tablero de indicadores) y SCRUM-32 (exportar reportes a hoja de cálculo).
