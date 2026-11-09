-- =====================================================================
-- RiTech SAS  |  db_ritech_exports  |  Sprint 2 rev. B  |  MySQL 8.0.16+
-- Sistema de Gestion de Exportaciones de Cafe y Cacao hacia la UE
-- Documento base: INF-RITECH-S2-v1.0
--
-- REVISION B. Correcciones sobre la version del 4 de octubre, todas probadas
-- contra MySQL 8.0.46. Cada una esta marcada con su codigo en el punto del
-- script donde se aplico:
--
--   C-01  Los CHECK de formato (codigo de pais, EORI) eran inoperantes: la
--         colacion utf8mb4_es_0900_ai_ci ignora mayusculas, asi que 'de'
--         pasaba como pais valido. Ahora usan REGEXP_LIKE con match 'c'.
--   C-02  validaciones_lote.fecha_ejecucion tenia precision de segundos y dos
--         corridas seguidas del validador compartian el mismo instante: el
--         dictamen vigente salia de la mezcla de ambas. Ahora DATETIME(6) y
--         un unico instante por corrida.
--   C-03  trg_lotfin_bu: la suma de aportes de finca se podia llevar al 140%
--         con un UPDATE porque solo el INSERT estaba protegido.
--   C-04  trg_ordlot_bu: los kg asignados se podian subir por encima del peso
--         neto del lote con un UPDATE, por el mismo motivo.
--   C-05  trg_ordlot_ai/au/ad: valor_total se mantenia a mano y quedaba
--         desincronizado. Ahora lo calcula la base.
--   C-06  chk_humedad admitia hasta 20% e impedia registrar la humedad real de
--         un lote fuera de norma, que es justo el dato que hay que conservar.
--         El limite comercial lo aplica R-UE-006, no el dominio.
--   C-07  auditoria.id_usuario era NOT NULL y el trigger imputaba el cambio al
--         usuario 1 cuando el backend no identificaba al autor: falsificaba la
--         pista de auditoria. Ahora admite NULL = autor no identificado.
--   C-08  R-UE-008 (EORI del importador) se evaluaba sobre el lote suelto y
--         daba CUMPLE por conjunto vacio, mientras trg_ordlot_bi exigia el
--         dictamen antes de asignar: la regla nunca verificaba nada. Se
--         introduce reglas_normativas_ue.ambito y la aplica trg_ordlot_bi.
--   C-09  seguimiento_envios.estado no tenia dominio y aceptaba texto libre.
--   C-10  Indices de reporte por fecha que faltaban.
--   C-11  trg_ordlot_ai/ad mantienen estado_lote (ASIGNADO / vuelta a
--         APROBADO), que tambien se ponia a mano desde el script.
--   C-12  envios: BL unico y numero de contenedor segun ISO 6346.
--   C-13  precio_unitario_usd pasa a precio_unitario: la orden fija su moneda
--         en EUR o USD y el nombre de la columna imponia USD; valor_total
--         mezclaba monedas.
--   C-14  Un lote RECHAZADO quedaba muerto porque la reinspeccion no podia
--         devolverlo al circuito.
--   C-15  La auditoria solo cubria clientes_ue; el lote y su inspeccion, que
--         son la evidencia EUDR, no dejaban rastro.
--   C-16  El cursor de sp_validar_lote dependia de variables locales sin
--         resolver en el momento de declararlo.
--   C-17  NULLIF en la division de geolocalizacion_fincas (lote sin fincas).
--   C-18  Los usuarios semilla se insertaban con id_rol literal 1..4.
--   C-19  Los permisos por rol tambien se asignaban por id literal.
--   C-20  El juego de demostracion tenia fechas fijas y dejaba de poder
--         ejecutarse el 2027-03-06, cuando el certificado semilla vence y
--         trg_lotcert_bi lo rechaza. Ahora son relativas a CURDATE().
--   C-21  Los conteos del cierre no coincidian con el esquema (decia 25 FK y
--         10 triggers donde hay 26 y 19).
--   C-22  validaciones_lote no guardaba el criterio aplicado. Como el catalogo
--         de reglas es editable, al cambiar un limite el historico perdia el
--         dato contra el que se habia juzgado el lote y dejaba de ser
--         reproducible ante una auditoria.
--
-- Nota de operacion: el backend debe fijar en cada sesion
--   SET @app_id_usuario = <id>;  SET @app_ip_origen = '<ip>';
-- o la auditoria quedara sin autor (ver C-07).
-- Requiere MySQL 8.0.16 o superior (antes de esa version los CHECK
-- se aceptan pero NO se aplican).
-- Orden de ejecucion: de arriba a abajo, de una sola pasada.
-- =====================================================================

-- DROP DATABASE IF EXISTS db_ritech_exports;   -- descomentar solo para partir de cero

CREATE DATABASE IF NOT EXISTS db_ritech_exports
  CHARACTER SET utf8mb4
  COLLATE       utf8mb4_es_0900_ai_ci;

USE db_ritech_exports;

SET FOREIGN_KEY_CHECKS = 1;


-- =====================================================================
-- BLOQUE A - SEGURIDAD Y ACCESO
-- =====================================================================

CREATE TABLE roles (
  id_rol      INT          NOT NULL AUTO_INCREMENT,
  nombre_rol  VARCHAR(50)  NOT NULL,
  descripcion VARCHAR(200) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_rol),
  UNIQUE KEY uq_roles_nombre (nombre_rol)
) ENGINE = InnoDB;

CREATE TABLE permisos (
  id_permiso  INT          NOT NULL AUTO_INCREMENT,
  codigo      VARCHAR(60)  NOT NULL,
  modulo      VARCHAR(40)  NOT NULL,
  descripcion VARCHAR(200) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_permiso),
  UNIQUE KEY uq_permisos_codigo (codigo)
) ENGINE = InnoDB;

-- Tabla puente R20: roles <-> permisos
CREATE TABLE roles_permisos (
  id_rol     INT      NOT NULL,
  id_permiso INT      NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_rol, id_permiso),
  CONSTRAINT fk_rolperm_rol
    FOREIGN KEY (id_rol) REFERENCES roles (id_rol)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_rolperm_permiso
    FOREIGN KEY (id_permiso) REFERENCES permisos (id_permiso)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB;

-- R1: roles -> usuarios   |   1:0..1 usuarios -> clientes_ue (FK al final, Bloque E)
CREATE TABLE usuarios (
  id_usuario      INT          NOT NULL AUTO_INCREMENT,
  id_rol          INT          NOT NULL,
  id_cliente      INT          NULL,
  nombre_completo VARCHAR(120) NOT NULL,
  email           VARCHAR(120) NOT NULL,
  password_hash   VARCHAR(255) NOT NULL,
  estado          VARCHAR(15)  NOT NULL DEFAULT 'ACTIVO',
  ultimo_acceso   DATETIME     NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_usuario),
  UNIQUE KEY uq_usuarios_email   (email),
  UNIQUE KEY uq_usuarios_cliente (id_cliente),
  CONSTRAINT fk_usuarios_rol
    FOREIGN KEY (id_rol) REFERENCES roles (id_rol)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_estado_usuario
    CHECK (estado IN ('ACTIVO','INACTIVO','BLOQUEADO')),
  CONSTRAINT chk_email_formato
    CHECK (REGEXP_LIKE(email, '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+[.][A-Za-z]{2,}$'))
) ENGINE = InnoDB;

-- R16: usuarios -> auditoria
CREATE TABLE auditoria (
  id_auditoria   BIGINT      NOT NULL AUTO_INCREMENT,
  id_usuario     INT         NULL,   -- NULL = autor no identificado por el backend
  tabla_afectada VARCHAR(60) NOT NULL,
  id_registro    VARCHAR(40) NOT NULL,
  accion         VARCHAR(10) NOT NULL,
  valor_anterior JSON        NULL,
  valor_nuevo    JSON        NULL,
  fecha_hora     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ip_origen      VARCHAR(45) NULL,
  PRIMARY KEY (id_auditoria),
  CONSTRAINT fk_auditoria_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_accion_auditoria
    CHECK (accion IN ('INSERT','UPDATE','DELETE'))
) ENGINE = InnoDB;


-- =====================================================================
-- BLOQUE B - ORIGEN AGRICOLA Y TRAZABILIDAD
-- =====================================================================

CREATE TABLE productores (
  id_productor INT          NOT NULL AUTO_INCREMENT,
  nit_o_cedula VARCHAR(20)  NOT NULL,
  nombre       VARCHAR(120) NOT NULL,
  municipio    VARCHAR(80)  NOT NULL,
  departamento VARCHAR(80)  NOT NULL,
  telefono     VARCHAR(25)  NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_productor),
  UNIQUE KEY uq_productores_nit (nit_o_cedula)
) ENGINE = InnoDB;

-- R2: productores -> fincas
CREATE TABLE fincas (
  id_finca                         INT           NOT NULL AUTO_INCREMENT,
  id_productor                     INT           NOT NULL,
  nombre_finca                     VARCHAR(120)  NOT NULL,
  area_hectareas                   DECIMAL(8,2)  NOT NULL,
  latitud                          DECIMAL(9,6)  NOT NULL,
  longitud                         DECIMAL(9,6)  NOT NULL,
  poligono_geojson                 JSON          NULL,
  fecha_verificacion_deforestacion DATE          NULL,
  created_at                       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_finca),
  KEY idx_fincas_productor (id_productor),
  CONSTRAINT fk_fincas_productor
    FOREIGN KEY (id_productor) REFERENCES productores (id_productor)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_area_finca CHECK (area_hectareas > 0),
  CONSTRAINT chk_latitud    CHECK (latitud  BETWEEN  -90 AND  90),
  CONSTRAINT chk_longitud   CHECK (longitud BETWEEN -180 AND 180),
  CONSTRAINT chk_poligono_eudr
    CHECK (area_hectareas <= 4 OR poligono_geojson IS NOT NULL)
) ENGINE = InnoDB;

CREATE TABLE productos (
  id_producto      INT          NOT NULL AUTO_INCREMENT,
  tipo             VARCHAR(10)  NOT NULL,
  nombre_comercial VARCHAR(100) NOT NULL,
  variedad         VARCHAR(80)  NULL,
  codigo_hs        VARCHAR(10)  NOT NULL,
  unidad_medida    VARCHAR(10)  NOT NULL DEFAULT 'KG',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_producto),
  CONSTRAINT chk_tipo_producto CHECK (tipo IN ('CAFE','CACAO'))
) ENGINE = InnoDB;


-- =====================================================================
-- BLOQUE C - LOTES DE EXPORTACION Y CONTROL DE CALIDAD
-- =====================================================================

-- Entidad nucleo. R3: productos -> lotes   R4: usuarios -> lotes
CREATE TABLE lotes_exportacion (
  id_lote             INT           NOT NULL AUTO_INCREMENT,
  codigo_lote         VARCHAR(30)   NOT NULL,
  id_producto         INT           NOT NULL,
  id_usuario_registra INT           NOT NULL,
  peso_neto_kg        DECIMAL(10,2) NOT NULL,
  peso_bruto_kg       DECIMAL(10,2) NOT NULL,
  fecha_cosecha       DATE          NOT NULL,
  fecha_registro      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  estado_lote         VARCHAR(20)   NOT NULL DEFAULT 'REGISTRADO',
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_lote),
  UNIQUE KEY uq_lotes_codigo (codigo_lote),
  KEY idx_lotes_estado   (estado_lote),
  KEY idx_lotes_producto (id_producto),
  CONSTRAINT fk_lotes_producto
    FOREIGN KEY (id_producto) REFERENCES productos (id_producto)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_lotes_usuario
    FOREIGN KEY (id_usuario_registra) REFERENCES usuarios (id_usuario)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_peso_neto   CHECK (peso_neto_kg > 0),
  CONSTRAINT chk_pesos       CHECK (peso_bruto_kg >= peso_neto_kg),
  CONSTRAINT chk_estado_lote CHECK (estado_lote IN
    ('REGISTRADO','EN_INSPECCION','APROBADO','RECHAZADO','ASIGNADO','EXPORTADO'))
  -- La regla "fecha_cosecha no futura" va en trigger: MySQL prohibe
  -- funciones no deterministas como CURDATE() dentro de un CHECK.
) ENGINE = InnoDB;

-- Tabla puente R17: lotes <-> fincas (nucleo de la trazabilidad EUDR)
CREATE TABLE lotes_fincas (
  id_lote           INT           NOT NULL,
  id_finca          INT           NOT NULL,
  porcentaje_aporte DECIMAL(5,2)  NOT NULL,
  kg_aportados      DECIMAL(10,2) NOT NULL,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_lote, id_finca),
  KEY idx_lotes_fincas_finca (id_finca),
  CONSTRAINT fk_lotfin_lote
    FOREIGN KEY (id_lote) REFERENCES lotes_exportacion (id_lote)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_lotfin_finca
    FOREIGN KEY (id_finca) REFERENCES fincas (id_finca)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_aporte       CHECK (porcentaje_aporte > 0 AND porcentaje_aporte <= 100),
  CONSTRAINT chk_kg_aportados CHECK (kg_aportados > 0)
) ENGINE = InnoDB;

-- R5: lotes -> inspecciones   R6: usuarios -> inspecciones
CREATE TABLE inspecciones_calidad (
  id_inspeccion        INT          NOT NULL AUTO_INCREMENT,
  id_lote              INT          NOT NULL,
  id_usuario_inspector INT          NOT NULL,
  fecha_inspeccion     DATE         NOT NULL DEFAULT (CURRENT_DATE),
  humedad_porcentaje   DECIMAL(5,2) NULL,
  defectos_por_300g    INT          NULL,
  puntaje_taza         DECIMAL(5,2) NULL,
  ocratoxina_ppb       DECIMAL(6,2) NULL,
  aflatoxinas_ppb      DECIMAL(6,2) NULL,   -- agregada: R-UE-005 no tenia donde medirse
  resultado            VARCHAR(15)  NOT NULL,
  observaciones        TEXT         NULL,
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_inspeccion),
  KEY idx_insp_lote (id_lote, fecha_inspeccion),
  CONSTRAINT fk_insp_lote
    FOREIGN KEY (id_lote) REFERENCES lotes_exportacion (id_lote)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_insp_inspector
    FOREIGN KEY (id_usuario_inspector) REFERENCES usuarios (id_usuario)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_humedad      CHECK (humedad_porcentaje BETWEEN 0 AND 100),
  CONSTRAINT chk_defectos     CHECK (defectos_por_300g >= 0),
  CONSTRAINT chk_puntaje_taza CHECK (puntaje_taza BETWEEN 0 AND 100),
  CONSTRAINT chk_ocratoxina   CHECK (ocratoxina_ppb  >= 0),
  CONSTRAINT chk_aflatoxinas  CHECK (aflatoxinas_ppb >= 0),
  -- Corregido respecto al Anexo C: sin "observaciones IS NOT NULL" la
  -- restriccion devuelve NULL y un CHECK nulo se considera cumplido,
  -- por lo que un RECHAZADO sin observacion pasaba sin error (CP-024).
  CONSTRAINT chk_resultado_insp CHECK (
    resultado IN ('APROBADO','RECHAZADO','CONDICIONADO')
    AND ( resultado <> 'RECHAZADO'
          OR (observaciones IS NOT NULL AND CHAR_LENGTH(observaciones) >= 20) )
  )
) ENGINE = InnoDB;


-- =====================================================================
-- BLOQUE D - CERTIFICACIONES Y CUMPLIMIENTO NORMATIVO
-- =====================================================================

CREATE TABLE certificaciones (
  id_certificacion INT          NOT NULL AUTO_INCREMENT,
  codigo           VARCHAR(30)  NOT NULL,
  nombre           VARCHAR(120) NOT NULL,
  entidad_emisora  VARCHAR(120) NULL,
  obligatoria_ue   BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_certificacion),
  UNIQUE KEY uq_cert_codigo (codigo)
) ENGINE = InnoDB;

-- Tabla puente R18: lotes <-> certificaciones
CREATE TABLE lotes_certificaciones (
  id_lote           INT          NOT NULL,
  id_certificacion  INT          NOT NULL,
  numero_documento  VARCHAR(60)  NOT NULL,
  fecha_emision     DATE         NOT NULL,
  fecha_vencimiento DATE         NOT NULL,
  url_soporte       VARCHAR(300) NULL,
  estado_validacion VARCHAR(15)  NOT NULL DEFAULT 'VIGENTE',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_lote, id_certificacion),
  KEY idx_cert_vencim (fecha_vencimiento),
  CONSTRAINT fk_lotcert_lote
    FOREIGN KEY (id_lote) REFERENCES lotes_exportacion (id_lote)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_lotcert_cert
    FOREIGN KEY (id_certificacion) REFERENCES certificaciones (id_certificacion)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_vigencia_cert     CHECK (fecha_vencimiento > fecha_emision),
  CONSTRAINT chk_estado_validacion CHECK (estado_validacion IN ('VIGENTE','VENCIDO','RECHAZADO'))
) ENGINE = InnoDB;

-- Catalogo administrable de normativa UE (decision arquitectonica del Sprint 2)
CREATE TABLE reglas_normativas_ue (
  id_regla             INT          NOT NULL AUTO_INCREMENT,
  codigo_regla         VARCHAR(20)  NOT NULL,
  normativa_referencia VARCHAR(120) NOT NULL,
  id_producto          INT          NULL,
  aplica_tipo          VARCHAR(10)  NOT NULL DEFAULT 'AMBOS',  -- agregada: columna "Aplica a" del apartado 4.4
  ambito               VARCHAR(10)  NOT NULL DEFAULT 'LOTE',   -- LOTE: la evalua sp_validar_lote; ORDEN: la evalua trg_ordlot_bi
  parametro            VARCHAR(60)  NOT NULL,
  operador             VARCHAR(10)  NOT NULL,
  valor_limite         VARCHAR(40)  NOT NULL,
  mensaje_error        VARCHAR(300) NOT NULL,
  activa               BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_regla),
  UNIQUE KEY uq_reglas_codigo (codigo_regla),
  CONSTRAINT fk_reglas_producto
    FOREIGN KEY (id_producto) REFERENCES productos (id_producto)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_operador_regla CHECK (operador IN ('=','<>','<','<=','>','>=')),
  CONSTRAINT chk_aplica_tipo    CHECK (aplica_tipo IN ('CAFE','CACAO','AMBOS')),
  CONSTRAINT chk_ambito_regla   CHECK (ambito IN ('LOTE','ORDEN'))
) ENGINE = InnoDB;

-- R14: lotes -> validaciones   R15: reglas -> validaciones
CREATE TABLE validaciones_lote (
  id_validacion   BIGINT      NOT NULL AUTO_INCREMENT,
  id_lote         INT         NOT NULL,
  id_regla        INT         NOT NULL,
  fecha_ejecucion DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  resultado       VARCHAR(12) NOT NULL,
  valor_evaluado  VARCHAR(60) NULL,
  operador_aplicado    VARCHAR(10) NULL,   -- copia del criterio vigente en la corrida:
  valor_limite_aplicado VARCHAR(40) NULL,  -- el catalogo es editable y el historico debe ser reproducible
  PRIMARY KEY (id_validacion),
  KEY idx_valid_lote (id_lote, fecha_ejecucion DESC),
  CONSTRAINT fk_valid_lote
    FOREIGN KEY (id_lote) REFERENCES lotes_exportacion (id_lote)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_valid_regla
    FOREIGN KEY (id_regla) REFERENCES reglas_normativas_ue (id_regla)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_resultado_valid CHECK (resultado IN ('CUMPLE','NO_CUMPLE'))
) ENGINE = InnoDB;


-- =====================================================================
-- BLOQUE E - COMERCIAL, DOCUMENTAL Y LOGISTICO
-- =====================================================================

CREATE TABLE paises (
  id_pais      INT         NOT NULL AUTO_INCREMENT,
  codigo_iso2  CHAR(2)     NOT NULL,
  nombre       VARCHAR(80) NOT NULL,
  pertenece_ue BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_pais),
  UNIQUE KEY uq_paises_iso2 (codigo_iso2),
  CONSTRAINT chk_codigo_iso2 CHECK (REGEXP_LIKE(codigo_iso2, '^[A-Z]{2}$', 'c'))
) ENGINE = InnoDB;

-- R7: paises -> clientes_ue
CREATE TABLE clientes_ue (
  id_cliente     INT          NOT NULL AUTO_INCREMENT,
  razon_social   VARCHAR(160) NOT NULL,
  numero_eori    VARCHAR(20)  NOT NULL,
  nit_vat        VARCHAR(25)  NULL,
  id_pais        INT          NOT NULL,
  ciudad         VARCHAR(80)  NOT NULL,
  direccion      VARCHAR(200) NULL,
  email_contacto VARCHAR(120) NOT NULL,
  estado         VARCHAR(15)  NOT NULL DEFAULT 'ACTIVO',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_cliente),
  UNIQUE KEY uq_clientes_eori (numero_eori),
  KEY idx_clientes_pais_estado (id_pais, estado),
  CONSTRAINT fk_clientes_pais
    FOREIGN KEY (id_pais) REFERENCES paises (id_pais)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_estado_cliente CHECK (estado IN ('ACTIVO','INACTIVO')),
  CONSTRAINT chk_eori_formato
    CHECK (REGEXP_LIKE(numero_eori, '^[A-Z]{2}[A-Za-z0-9]{12,15}$', 'c')),
  CONSTRAINT chk_email_cliente
    CHECK (REGEXP_LIKE(email_contacto, '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+[.][A-Za-z]{2,}$'))
) ENGINE = InnoDB;

-- Cierre de la relacion 1:0..1 usuarios -> clientes_ue
ALTER TABLE usuarios
  ADD CONSTRAINT fk_usuarios_cliente
  FOREIGN KEY (id_cliente) REFERENCES clientes_ue (id_cliente)
  ON DELETE SET NULL ON UPDATE CASCADE;

-- R8: clientes -> ordenes   R9: usuarios -> ordenes
CREATE TABLE ordenes_exportacion (
  id_orden          INT           NOT NULL AUTO_INCREMENT,
  numero_orden      VARCHAR(30)   NOT NULL,
  id_cliente        INT           NOT NULL,
  id_usuario_gestor INT           NOT NULL,
  fecha_orden       DATE          NOT NULL DEFAULT (CURRENT_DATE),
  incoterm          VARCHAR(5)    NOT NULL,
  moneda            CHAR(3)       NOT NULL DEFAULT 'EUR',
  valor_total       DECIMAL(14,2) NOT NULL DEFAULT 0,
  estado_orden      VARCHAR(20)   NOT NULL DEFAULT 'BORRADOR',
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_orden),
  UNIQUE KEY uq_ordenes_numero (numero_orden),
  KEY idx_ordenes_cliente (id_cliente, estado_orden),
  CONSTRAINT fk_ordenes_cliente
    FOREIGN KEY (id_cliente) REFERENCES clientes_ue (id_cliente)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_ordenes_gestor
    FOREIGN KEY (id_usuario_gestor) REFERENCES usuarios (id_usuario)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_incoterm     CHECK (incoterm IN ('FOB','CIF','DAP','EXW','FCA','CFR','CPT','DDP')),
  CONSTRAINT chk_moneda       CHECK (moneda IN ('EUR','USD')),
  CONSTRAINT chk_valor_total  CHECK (valor_total >= 0),
  CONSTRAINT chk_estado_orden CHECK (estado_orden IN
    ('BORRADOR','CONFIRMADA','EN_TRANSITO','ENTREGADA','ANULADA'))
) ENGINE = InnoDB;

-- Tabla puente R19: ordenes <-> lotes
CREATE TABLE ordenes_lotes (
  id_orden            INT           NOT NULL,
  id_lote             INT           NOT NULL,
  kg_asignados        DECIMAL(10,2) NOT NULL,
  precio_unitario DECIMAL(12,4) NOT NULL,
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_orden, id_lote),
  KEY idx_ordlot_lote (id_lote),
  CONSTRAINT fk_ordlot_orden
    FOREIGN KEY (id_orden) REFERENCES ordenes_exportacion (id_orden)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_ordlot_lote
    FOREIGN KEY (id_lote) REFERENCES lotes_exportacion (id_lote)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_kg_asignados_pos CHECK (kg_asignados > 0),
  CONSTRAINT chk_precio_unitario  CHECK (precio_unitario > 0)
) ENGINE = InnoDB;

CREATE TABLE transportistas (
  id_transportista INT          NOT NULL AUTO_INCREMENT,
  nombre           VARCHAR(120) NOT NULL,
  tipo             VARCHAR(12)  NOT NULL,
  contacto         VARCHAR(120) NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_transportista),
  CONSTRAINT chk_tipo_transportista CHECK (tipo IN ('NAVIERA','AEREA','TERRESTRE'))
) ENGINE = InnoDB;

-- R10: ordenes -> envios   R11: transportistas -> envios
CREATE TABLE envios (
  id_envio              INT         NOT NULL AUTO_INCREMENT,
  id_orden              INT         NOT NULL,
  id_transportista      INT         NOT NULL,
  numero_contenedor     VARCHAR(15) NULL,
  booking_bl            VARCHAR(40) NULL,
  puerto_origen         VARCHAR(80) NOT NULL,
  puerto_destino        VARCHAR(80) NOT NULL,
  fecha_estimada_zarpe  DATE        NULL,
  fecha_estimada_arribo DATE        NULL,
  estado_envio          VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADO',
  created_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_envio),
  UNIQUE KEY uq_envios_booking (booking_bl),
  KEY idx_envios_estado (estado_envio, fecha_estimada_arribo),
  KEY idx_envios_orden  (id_orden),
  CONSTRAINT fk_envios_orden
    FOREIGN KEY (id_orden) REFERENCES ordenes_exportacion (id_orden)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_envios_transportista
    FOREIGN KEY (id_transportista) REFERENCES transportistas (id_transportista)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_estado_envio CHECK (estado_envio IN
    ('PROGRAMADO','EN_PUERTO','EN_TRANSITO','ARRIBADO','ENTREGADO','INCIDENCIA')),
  CONSTRAINT chk_fechas_envio CHECK (
    fecha_estimada_zarpe IS NULL
    OR fecha_estimada_arribo IS NULL
    OR fecha_estimada_arribo >= fecha_estimada_zarpe),
  CONSTRAINT chk_contenedor_iso6346 CHECK (
    numero_contenedor IS NULL
    OR REGEXP_LIKE(numero_contenedor, '^[A-Z]{4}[0-9]{7}$', 'c'))
) ENGINE = InnoDB;

-- R12: envios -> seguimiento (bitacora inmutable, sin updated_at a proposito)
CREATE TABLE seguimiento_envios (
  id_seguimiento INT          NOT NULL AUTO_INCREMENT,
  id_envio       INT          NOT NULL,
  fecha_evento   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ubicacion      VARCHAR(120) NOT NULL,
  estado         VARCHAR(20)  NOT NULL,
  observacion    VARCHAR(300) NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_seguimiento),
  KEY idx_seg_envio (id_envio, fecha_evento),
  CONSTRAINT fk_seg_envio
    FOREIGN KEY (id_envio) REFERENCES envios (id_envio)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT chk_estado_seguimiento CHECK (estado IN
    ('PROGRAMADO','EN_PUERTO','EN_TRANSITO','ARRIBADO','ENTREGADO','INCIDENCIA'))
) ENGINE = InnoDB;

-- R13: ordenes -> documentos
CREATE TABLE documentos_exportacion (
  id_documento   INT          NOT NULL AUTO_INCREMENT,
  id_orden       INT          NOT NULL,
  tipo_documento VARCHAR(30)  NOT NULL,
  numero         VARCHAR(60)  NOT NULL,
  url_archivo    VARCHAR(300) NOT NULL,
  fecha_emision  DATE         NOT NULL DEFAULT (CURRENT_DATE),
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_documento),
  UNIQUE KEY uq_doc_orden_tipo (id_orden, tipo_documento),
  CONSTRAINT fk_doc_orden
    FOREIGN KEY (id_orden) REFERENCES ordenes_exportacion (id_orden)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_tipo_documento CHECK (tipo_documento IN
    ('FACTURA_COMERCIAL','LISTA_EMPAQUE','CERT_FITOSANITARIO',
     'CERT_ORIGEN','BL_CONOCIMIENTO_EMBARQUE','DDS_EUDR'))
) ENGINE = InnoDB;


-- =====================================================================
-- INDICE RESTANTE DEL APARTADO 2.7
-- (idx_lotes_codigo del informe es redundante: UNIQUE ya crea su indice)
-- =====================================================================

CREATE INDEX idx_auditoria_tabla  ON auditoria (tabla_afectada, fecha_hora DESC);
CREATE INDEX idx_lotes_cosecha    ON lotes_exportacion (fecha_cosecha);
CREATE INDEX idx_ordenes_fecha    ON ordenes_exportacion (fecha_orden, estado_orden);
CREATE INDEX idx_lotcert_estado   ON lotes_certificaciones (estado_validacion, fecha_vencimiento);


-- =====================================================================
-- DISPARADORES Y PROCEDIMIENTOS
-- =====================================================================

DELIMITER $$

-- Fecha de cosecha no futura (sustituye al CHECK imposible en MySQL)
CREATE TRIGGER trg_lotes_fecha_bi
BEFORE INSERT ON lotes_exportacion
FOR EACH ROW
BEGIN
  IF NEW.fecha_cosecha > CURDATE() THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'La fecha de cosecha no puede ser futura.';
  END IF;
END$$

CREATE TRIGGER trg_lotes_fecha_bu
BEFORE UPDATE ON lotes_exportacion
FOR EACH ROW
BEGIN
  IF NEW.fecha_cosecha > CURDATE() THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'La fecha de cosecha no puede ser futura.';
  END IF;
END$$

-- Consistencia por fila de los aportes de finca
CREATE TRIGGER trg_lotfin_bi
BEFORE INSERT ON lotes_fincas
FOR EACH ROW
BEGIN
  DECLARE v_peso_neto    DECIMAL(10,2);
  DECLARE v_suma_pct     DECIMAL(7,2);
  DECLARE v_kg_esperados DECIMAL(10,2);
  DECLARE v_duplicada    INT;

  -- Se revisa primero el duplicado: el trigger corre ANTES que la PRIMARY KEY
  -- compuesta, y sin esto el usuario recibiria el mensaje de "suma > 100%"
  -- en lugar del de finca duplicada que exige CP-018.
  SELECT COUNT(*) INTO v_duplicada
    FROM lotes_fincas
   WHERE id_lote = NEW.id_lote AND id_finca = NEW.id_finca;

  IF v_duplicada > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'La finca ya se encuentra asociada a este lote.';
  END IF;

  SELECT peso_neto_kg INTO v_peso_neto
    FROM lotes_exportacion
   WHERE id_lote = NEW.id_lote;

  SELECT IFNULL(SUM(porcentaje_aporte), 0) INTO v_suma_pct
    FROM lotes_fincas
   WHERE id_lote = NEW.id_lote;

  IF v_suma_pct + NEW.porcentaje_aporte > 100.01 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'La suma de aportes de finca del lote no puede exceder 100%.';
  END IF;

  SET v_kg_esperados = v_peso_neto * NEW.porcentaje_aporte / 100;

  IF ABS(NEW.kg_aportados - v_kg_esperados) > v_peso_neto * 0.005 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Los kg aportados no corresponden al porcentaje ni al peso neto (tol. 0,5%).';
  END IF;
END$$

-- Mismas reglas sobre el UPDATE: sin este trigger la suma de aportes se podia
-- llevar a cualquier valor despues de cerrar la trazabilidad.
CREATE TRIGGER trg_lotfin_bu
BEFORE UPDATE ON lotes_fincas
FOR EACH ROW
BEGIN
  DECLARE v_peso_neto    DECIMAL(10,2);
  DECLARE v_suma_pct     DECIMAL(7,2);
  DECLARE v_kg_esperados DECIMAL(10,2);

  SELECT peso_neto_kg INTO v_peso_neto
    FROM lotes_exportacion
   WHERE id_lote = NEW.id_lote;

  -- La fila que se esta modificando se excluye de la suma.
  SELECT IFNULL(SUM(porcentaje_aporte), 0) INTO v_suma_pct
    FROM lotes_fincas
   WHERE id_lote = NEW.id_lote
     AND NOT (id_lote = OLD.id_lote AND id_finca = OLD.id_finca);

  IF v_suma_pct + NEW.porcentaje_aporte > 100.01 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'La suma de aportes de finca del lote no puede exceder 100%.';
  END IF;

  SET v_kg_esperados = v_peso_neto * NEW.porcentaje_aporte / 100;

  IF ABS(NEW.kg_aportados - v_kg_esperados) > v_peso_neto * 0.005 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Los kg aportados no corresponden al porcentaje ni al peso neto (tol. 0,5%).';
  END IF;
END$$

-- El pais del importador debe ser miembro de la UE (chk_pais_ue del informe)
CREATE TRIGGER trg_clientes_pais_bi
BEFORE INSERT ON clientes_ue
FOR EACH ROW
BEGIN
  DECLARE v_ue BOOLEAN;
  SELECT pertenece_ue INTO v_ue FROM paises WHERE id_pais = NEW.id_pais;
  IF v_ue IS NULL OR v_ue = FALSE THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'El pais del importador debe ser miembro de la Union Europea.';
  END IF;
END$$

CREATE TRIGGER trg_clientes_pais_bu
BEFORE UPDATE ON clientes_ue
FOR EACH ROW
BEGIN
  DECLARE v_ue BOOLEAN;
  SELECT pertenece_ue INTO v_ue FROM paises WHERE id_pais = NEW.id_pais;
  IF v_ue IS NULL OR v_ue = FALSE THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'El pais del importador debe ser miembro de la Union Europea.';
  END IF;
END$$

-- No se admite cargar un certificado ya vencido (CP-028)
CREATE TRIGGER trg_lotcert_bi
BEFORE INSERT ON lotes_certificaciones
FOR EACH ROW
BEGIN
  -- Primero la coherencia entre fechas (CP-029). El trigger corre antes que
  -- chk_vigencia_cert, asi que sin esta rama el usuario recibiria el mensaje
  -- de "certificado vencido" cuando el problema es el orden de las fechas.
  IF NEW.fecha_vencimiento <= NEW.fecha_emision THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'La fecha de vencimiento debe ser posterior a la fecha de emision.';
  END IF;

  IF NEW.fecha_vencimiento <= CURDATE() THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'No es posible cargar un certificado ya vencido.';
  END IF;
END$$

-- La inspeccion mueve el estado del lote (maquina de estados, RIT-10/RIT-11)
CREATE TRIGGER trg_insp_estado_ai
AFTER INSERT ON inspecciones_calidad
FOR EACH ROW
BEGIN
  UPDATE lotes_exportacion
     SET estado_lote = CASE NEW.resultado
                         WHEN 'APROBADO'  THEN 'APROBADO'
                         WHEN 'RECHAZADO' THEN 'RECHAZADO'
                         ELSE 'EN_INSPECCION'
                       END
   WHERE id_lote = NEW.id_lote
     AND estado_lote IN ('REGISTRADO','EN_INSPECCION','RECHAZADO');
END$$

-- Compuerta de cumplimiento: estado APROBADO + dictamen CUMPLE + kg disponibles
CREATE TRIGGER trg_ordlot_bi
BEFORE INSERT ON ordenes_lotes
FOR EACH ROW
BEGIN
  DECLARE v_estado      VARCHAR(20);
  DECLARE v_peso_neto   DECIMAL(10,2);
  DECLARE v_ya_asignado DECIMAL(12,2);
  DECLARE v_validadas   INT;
  DECLARE v_incumplidas INT;
  DECLARE v_importador_invalido INT;

  SELECT estado_lote, peso_neto_kg INTO v_estado, v_peso_neto
    FROM lotes_exportacion WHERE id_lote = NEW.id_lote;

  IF v_estado IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El lote indicado no existe.';
  END IF;

  IF v_estado <> 'APROBADO' THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Solo pueden asignarse lotes en estado APROBADO.';
  END IF;

  SELECT COUNT(*), IFNULL(SUM(resultado = 'NO_CUMPLE'), 0)
    INTO v_validadas, v_incumplidas
    FROM validaciones_lote
   WHERE id_lote = NEW.id_lote
     AND fecha_ejecucion = (SELECT MAX(fecha_ejecucion)
                              FROM validaciones_lote WHERE id_lote = NEW.id_lote);

  IF v_validadas = 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'El lote no tiene validacion normativa vigente. Ejecute sp_validar_lote.';
  END IF;

  IF v_incumplidas > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'El lote tiene dictamen NO_CUMPLE. No puede asignarse a una orden.';
  END IF;

  SELECT COUNT(*) INTO v_importador_invalido
    FROM ordenes_exportacion o
    JOIN clientes_ue cl ON cl.id_cliente = o.id_cliente
    JOIN paises      pa ON pa.id_pais    = cl.id_pais
   WHERE o.id_orden = NEW.id_orden
     AND (pa.pertenece_ue = FALSE
          OR cl.estado <> 'ACTIVO'
          OR NOT REGEXP_LIKE(cl.numero_eori, '^[A-Z]{2}[A-Za-z0-9]{12,15}$', 'c'));

  IF v_importador_invalido > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'El importador de la orden no cumple R-UE-008: EORI invalido, pais fuera de la UE o cliente inactivo.';
  END IF;

  SELECT IFNULL(SUM(kg_asignados), 0) INTO v_ya_asignado
    FROM ordenes_lotes WHERE id_lote = NEW.id_lote;

  IF v_ya_asignado + NEW.kg_asignados > v_peso_neto THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Los kg asignados exceden el peso neto disponible del lote.';
  END IF;
END$$

CREATE TRIGGER trg_ordlot_bu
BEFORE UPDATE ON ordenes_lotes
FOR EACH ROW
BEGIN
  DECLARE v_peso_neto DECIMAL(10,2);
  DECLARE v_otros     DECIMAL(12,2);

  SELECT peso_neto_kg INTO v_peso_neto
    FROM lotes_exportacion WHERE id_lote = NEW.id_lote;

  SELECT IFNULL(SUM(kg_asignados), 0) INTO v_otros
    FROM ordenes_lotes
   WHERE id_lote = NEW.id_lote
     AND NOT (id_orden = OLD.id_orden AND id_lote = OLD.id_lote);

  IF v_otros + NEW.kg_asignados > v_peso_neto THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Los kg asignados exceden el peso neto disponible del lote.';
  END IF;
END$$

-- valor_total y estado_lote son datos derivados: los mantiene la base, no el backend.
CREATE TRIGGER trg_ordlot_ai
AFTER INSERT ON ordenes_lotes
FOR EACH ROW
BEGIN
  UPDATE ordenes_exportacion
     SET valor_total = (SELECT IFNULL(SUM(kg_asignados * precio_unitario), 0)
                          FROM ordenes_lotes WHERE id_orden = NEW.id_orden)
   WHERE id_orden = NEW.id_orden;

  UPDATE lotes_exportacion
     SET estado_lote = 'ASIGNADO'
   WHERE id_lote = NEW.id_lote
     AND estado_lote = 'APROBADO';
END$$

CREATE TRIGGER trg_ordlot_au
AFTER UPDATE ON ordenes_lotes
FOR EACH ROW
BEGIN
  UPDATE ordenes_exportacion
     SET valor_total = (SELECT IFNULL(SUM(kg_asignados * precio_unitario), 0)
                          FROM ordenes_lotes WHERE id_orden = NEW.id_orden)
   WHERE id_orden = NEW.id_orden;
END$$

CREATE TRIGGER trg_ordlot_ad
AFTER DELETE ON ordenes_lotes
FOR EACH ROW
BEGIN
  UPDATE ordenes_exportacion
     SET valor_total = (SELECT IFNULL(SUM(kg_asignados * precio_unitario), 0)
                          FROM ordenes_lotes WHERE id_orden = OLD.id_orden)
   WHERE id_orden = OLD.id_orden;

  -- El lote vuelve a quedar disponible si ya no esta en ninguna orden.
  UPDATE lotes_exportacion
     SET estado_lote = 'APROBADO'
   WHERE id_lote = OLD.id_lote
     AND estado_lote = 'ASIGNADO'
     AND NOT EXISTS (SELECT 1 FROM ordenes_lotes WHERE id_lote = OLD.id_lote);
END$$

-- Auditoria automatica (RIT-03). Patron replicable al resto de maestras.
-- El backend debe fijar: SET @app_id_usuario = <id>;  SET @app_ip_origen = '<ip>';
CREATE TRIGGER trg_aud_clientes_ai
AFTER INSERT ON clientes_ue
FOR EACH ROW
BEGIN
  INSERT INTO auditoria (id_usuario, tabla_afectada, id_registro, accion, valor_nuevo, ip_origen)
  VALUES (@app_id_usuario, 'clientes_ue', CAST(NEW.id_cliente AS CHAR), 'INSERT',
          JSON_OBJECT('razon_social', NEW.razon_social, 'numero_eori', NEW.numero_eori,
                      'id_pais', NEW.id_pais, 'ciudad', NEW.ciudad,
                      'email_contacto', NEW.email_contacto, 'estado', NEW.estado),
          @app_ip_origen);
END$$

CREATE TRIGGER trg_aud_clientes_au
AFTER UPDATE ON clientes_ue
FOR EACH ROW
BEGIN
  INSERT INTO auditoria (id_usuario, tabla_afectada, id_registro, accion,
                         valor_anterior, valor_nuevo, ip_origen)
  VALUES (@app_id_usuario, 'clientes_ue', CAST(NEW.id_cliente AS CHAR), 'UPDATE',
          JSON_OBJECT('razon_social', OLD.razon_social, 'numero_eori', OLD.numero_eori,
                      'id_pais', OLD.id_pais, 'ciudad', OLD.ciudad,
                      'email_contacto', OLD.email_contacto, 'estado', OLD.estado),
          JSON_OBJECT('razon_social', NEW.razon_social, 'numero_eori', NEW.numero_eori,
                      'id_pais', NEW.id_pais, 'ciudad', NEW.ciudad,
                      'email_contacto', NEW.email_contacto, 'estado', NEW.estado),
          @app_ip_origen);
END$$

CREATE TRIGGER trg_aud_clientes_ad
AFTER DELETE ON clientes_ue
FOR EACH ROW
BEGIN
  INSERT INTO auditoria (id_usuario, tabla_afectada, id_registro, accion, valor_anterior, ip_origen)
  VALUES (@app_id_usuario, 'clientes_ue', CAST(OLD.id_cliente AS CHAR), 'DELETE',
          JSON_OBJECT('razon_social', OLD.razon_social, 'numero_eori', OLD.numero_eori,
                      'estado', OLD.estado),
          @app_ip_origen);
END$$

CREATE TRIGGER trg_aud_lotes_ai
AFTER INSERT ON lotes_exportacion
FOR EACH ROW
BEGIN
  INSERT INTO auditoria (id_usuario, tabla_afectada, id_registro, accion, valor_nuevo, ip_origen)
  VALUES (@app_id_usuario, 'lotes_exportacion', CAST(NEW.id_lote AS CHAR), 'INSERT',
          JSON_OBJECT('codigo_lote', NEW.codigo_lote, 'id_producto', NEW.id_producto,
                      'peso_neto_kg', NEW.peso_neto_kg, 'peso_bruto_kg', NEW.peso_bruto_kg,
                      'fecha_cosecha', NEW.fecha_cosecha, 'estado_lote', NEW.estado_lote),
          @app_ip_origen);
END$$

CREATE TRIGGER trg_aud_lotes_au
AFTER UPDATE ON lotes_exportacion
FOR EACH ROW
BEGIN
  INSERT INTO auditoria (id_usuario, tabla_afectada, id_registro, accion,
                         valor_anterior, valor_nuevo, ip_origen)
  VALUES (@app_id_usuario, 'lotes_exportacion', CAST(NEW.id_lote AS CHAR), 'UPDATE',
          JSON_OBJECT('peso_neto_kg', OLD.peso_neto_kg, 'peso_bruto_kg', OLD.peso_bruto_kg,
                      'fecha_cosecha', OLD.fecha_cosecha, 'estado_lote', OLD.estado_lote),
          JSON_OBJECT('peso_neto_kg', NEW.peso_neto_kg, 'peso_bruto_kg', NEW.peso_bruto_kg,
                      'fecha_cosecha', NEW.fecha_cosecha, 'estado_lote', NEW.estado_lote),
          @app_ip_origen);
END$$

CREATE TRIGGER trg_aud_insp_ai
AFTER INSERT ON inspecciones_calidad
FOR EACH ROW
BEGIN
  INSERT INTO auditoria (id_usuario, tabla_afectada, id_registro, accion, valor_nuevo, ip_origen)
  VALUES (@app_id_usuario, 'inspecciones_calidad', CAST(NEW.id_inspeccion AS CHAR), 'INSERT',
          JSON_OBJECT('id_lote', NEW.id_lote, 'id_usuario_inspector', NEW.id_usuario_inspector,
                      'fecha_inspeccion', NEW.fecha_inspeccion, 'resultado', NEW.resultado,
                      'humedad_porcentaje', NEW.humedad_porcentaje,
                      'ocratoxina_ppb', NEW.ocratoxina_ppb,
                      'aflatoxinas_ppb', NEW.aflatoxinas_ppb),
          @app_ip_origen);
END$$

-- Cierre de la trazabilidad: suma = 100% y kg = peso neto (+-0,5%)
CREATE PROCEDURE sp_cerrar_trazabilidad (IN p_id_lote INT)
BEGIN
  DECLARE v_peso_neto DECIMAL(10,2);
  DECLARE v_suma_pct  DECIMAL(7,2);
  DECLARE v_suma_kg   DECIMAL(12,2);
  DECLARE v_fincas    INT;

  SELECT peso_neto_kg INTO v_peso_neto
    FROM lotes_exportacion WHERE id_lote = p_id_lote;

  IF v_peso_neto IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El lote indicado no existe.';
  END IF;

  SELECT COUNT(*), IFNULL(SUM(porcentaje_aporte),0), IFNULL(SUM(kg_aportados),0)
    INTO v_fincas, v_suma_pct, v_suma_kg
    FROM lotes_fincas WHERE id_lote = p_id_lote;

  IF v_fincas = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El lote debe tener al menos una finca de origen.';
  END IF;

  IF ABS(v_suma_pct - 100) > 0.01 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'La suma de los aportes de finca debe ser exactamente 100%.';
  END IF;

  IF ABS(v_suma_kg - v_peso_neto) > v_peso_neto * 0.005 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'La suma de kg aportados no coincide con el peso neto (tol. 0,5%).';
  END IF;

  SELECT 'Trazabilidad completa' AS mensaje,
         v_fincas   AS fincas_origen,
         v_suma_pct AS suma_porcentaje,
         v_suma_kg  AS suma_kilogramos;
END$$

-- Motor de validacion normativa UE (RIT-15, 13 SP)
CREATE PROCEDURE sp_validar_lote (IN p_id_lote INT)
BEGIN
  DECLARE v_fin          INT DEFAULT 0;
  DECLARE v_id_regla     INT;
  DECLARE v_parametro    VARCHAR(60);
  DECLARE v_operador     VARCHAR(10);
  DECLARE v_valor_limite VARCHAR(40);
  DECLARE v_limite       DECIMAL(14,4);
  DECLARE v_valor        DECIMAL(14,4);
  DECLARE v_cumple       TINYINT;
  DECLARE v_existe       INT;
  DECLARE v_ejecucion    DATETIME(6);

  -- El cursor resuelve el tipo de producto en su propia consulta. Antes dependia
  -- de variables locales, un orden fragil porque MySQL exige declarar el cursor
  -- antes de poder asignarlas.
  DECLARE cur_reglas CURSOR FOR
    SELECT r.id_regla, r.parametro, r.operador, r.valor_limite
      FROM reglas_normativas_ue r
      JOIN lotes_exportacion    l ON l.id_lote     = p_id_lote
      JOIN productos            p ON p.id_producto = l.id_producto
     WHERE r.activa = TRUE
       AND r.ambito = 'LOTE'
       AND (r.aplica_tipo = 'AMBOS' OR r.aplica_tipo = p.tipo)
       AND (r.id_producto IS NULL   OR r.id_producto = p.id_producto)
     ORDER BY r.codigo_regla;

  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_fin = 1;

  SELECT COUNT(*) INTO v_existe
    FROM lotes_exportacion WHERE id_lote = p_id_lote;

  IF v_existe = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El lote indicado no existe.';
  END IF;

  -- Un unico instante para toda la corrida: es lo que identifica la validacion
  -- vigente. Con precision de segundos dos corridas seguidas se fusionaban y el
  -- dictamen salia de la mezcla de las dos.
  SET v_ejecucion = NOW(6);

  OPEN cur_reglas;

  bucle: LOOP
    FETCH cur_reglas INTO v_id_regla, v_parametro, v_operador, v_valor_limite;
    IF v_fin = 1 THEN
      LEAVE bucle;
    END IF;

    SET v_limite = CASE WHEN LOWER(v_valor_limite) IN ('true','si')
                        THEN 1
                        ELSE CAST(v_valor_limite AS DECIMAL(14,4))
                   END;

    SET v_valor = CASE v_parametro

      WHEN 'geolocalizacion_fincas' THEN (
        SELECT 100.0 * SUM(f.latitud IS NOT NULL AND f.longitud IS NOT NULL)
                     / NULLIF(COUNT(*), 0)
          FROM lotes_fincas lf
          JOIN fincas f ON f.id_finca = lf.id_finca
         WHERE lf.id_lote = p_id_lote)

      WHEN 'poligono_si_area_mayor_4ha' THEN (
        SELECT IF(COUNT(*) = 0, 1, 0)
          FROM lotes_fincas lf
          JOIN fincas f ON f.id_finca = lf.id_finca
         WHERE lf.id_lote = p_id_lote
           AND f.area_hectareas > 4
           AND f.poligono_geojson IS NULL)

      WHEN 'suma_aportes_finca' THEN (
        SELECT IFNULL(SUM(porcentaje_aporte), 0)
          FROM lotes_fincas WHERE id_lote = p_id_lote)

      WHEN 'ocratoxina_a_cafe' THEN (
        SELECT ocratoxina_ppb FROM inspecciones_calidad
         WHERE id_lote = p_id_lote
         ORDER BY fecha_inspeccion DESC, id_inspeccion DESC LIMIT 1)

      WHEN 'aflatoxinas_cacao' THEN (
        SELECT aflatoxinas_ppb FROM inspecciones_calidad
         WHERE id_lote = p_id_lote
         ORDER BY fecha_inspeccion DESC, id_inspeccion DESC LIMIT 1)

      WHEN 'humedad_cafe_verde' THEN (
        SELECT humedad_porcentaje FROM inspecciones_calidad
         WHERE id_lote = p_id_lote
         ORDER BY fecha_inspeccion DESC, id_inspeccion DESC LIMIT 1)

      WHEN 'certificado_fitosanitario_vigente' THEN (
        SELECT IF(COUNT(*) > 0, 1, 0)
          FROM lotes_certificaciones lc
          JOIN certificaciones c ON c.id_certificacion = lc.id_certificacion
         WHERE lc.id_lote = p_id_lote
           AND c.codigo = 'FITOSANITARIO'
           AND lc.fecha_vencimiento > CURDATE()
           AND lc.estado_validacion = 'VIGENTE')

      -- R-UE-008 (eori_importador_valido) no aparece aqui a proposito: es de
      -- ambito ORDEN y la aplica trg_ordlot_bi, que es el unico punto donde el
      -- importador ya se conoce. Evaluada sobre el lote suelto daba CUMPLE por
      -- conjunto vacio y la regla nunca llegaba a verificar nada.

      ELSE NULL
    END;

    IF v_valor IS NULL THEN
      SET v_cumple = 0;
    ELSE
      SET v_cumple = CASE v_operador
                       WHEN '='  THEN (ABS(v_valor - v_limite) <  0.0001)
                       WHEN '<>' THEN (ABS(v_valor - v_limite) >= 0.0001)
                       WHEN '<'  THEN (v_valor <  v_limite)
                       WHEN '<=' THEN (v_valor <= v_limite)
                       WHEN '>'  THEN (v_valor >  v_limite)
                       WHEN '>=' THEN (v_valor >= v_limite)
                       ELSE 0
                     END;
    END IF;

    INSERT INTO validaciones_lote (id_lote, id_regla, fecha_ejecucion, resultado,
                                   valor_evaluado, operador_aplicado, valor_limite_aplicado)
    VALUES (p_id_lote, v_id_regla, v_ejecucion,
            IF(v_cumple = 1, 'CUMPLE', 'NO_CUMPLE'),
            IFNULL(CAST(v_valor AS CHAR), 'SIN DATO'),
            v_operador, v_valor_limite);

  END LOOP bucle;

  CLOSE cur_reglas;

  SELECT l.codigo_lote,
         CASE WHEN SUM(v.resultado = 'NO_CUMPLE') = 0 THEN 'CUMPLE' ELSE 'NO_CUMPLE' END AS dictamen,
         COUNT(*)                       AS reglas_evaluadas,
         SUM(v.resultado = 'NO_CUMPLE') AS reglas_incumplidas
    FROM validaciones_lote v
    JOIN lotes_exportacion l ON l.id_lote = v.id_lote
   WHERE v.id_lote = p_id_lote
     AND v.fecha_ejecucion = v_ejecucion
   GROUP BY l.codigo_lote;
END$$

DELIMITER ;


-- =====================================================================
-- VISTAS DE CONSULTA
-- =====================================================================

CREATE VIEW v_dictamen_lote AS
SELECT l.id_lote,
       l.codigo_lote,
       l.estado_lote,
       MAX(v.fecha_ejecucion)                   AS ultima_validacion,
       COUNT(v.id_validacion)                   AS reglas_evaluadas,
       IFNULL(SUM(v.resultado = 'NO_CUMPLE'),0) AS reglas_incumplidas,
       CASE WHEN COUNT(v.id_validacion) = 0         THEN 'SIN_VALIDAR'
            WHEN SUM(v.resultado = 'NO_CUMPLE') = 0 THEN 'CUMPLE'
            ELSE 'NO_CUMPLE'
       END                                      AS dictamen
  FROM lotes_exportacion l
  LEFT JOIN validaciones_lote v
         ON v.id_lote = l.id_lote
        AND v.fecha_ejecucion = (SELECT MAX(fecha_ejecucion)
                                   FROM validaciones_lote WHERE id_lote = l.id_lote)
 GROUP BY l.id_lote, l.codigo_lote, l.estado_lote;

CREATE VIEW v_trazabilidad_lote AS
SELECT l.codigo_lote,
       p.nombre_comercial AS producto,
       p.codigo_hs,
       l.peso_neto_kg,
       l.estado_lote,
       pr.nombre          AS productor,
       f.nombre_finca,
       f.area_hectareas,
       f.latitud,
       f.longitud,
       lf.porcentaje_aporte,
       lf.kg_aportados,
       pr.municipio,
       pr.departamento
  FROM lotes_exportacion l
  JOIN productos    p  ON p.id_producto   = l.id_producto
  JOIN lotes_fincas lf ON lf.id_lote      = l.id_lote
  JOIN fincas       f  ON f.id_finca      = lf.id_finca
  JOIN productores  pr ON pr.id_productor = f.id_productor
 ORDER BY l.codigo_lote, lf.porcentaje_aporte DESC;


CREATE VIEW v_cumplimiento_importador AS
SELECT o.id_orden,
       o.numero_orden,
       o.estado_orden,
       cl.razon_social,
       cl.numero_eori,
       pa.codigo_iso2,
       pa.pertenece_ue,
       cl.estado AS estado_cliente,
       CASE WHEN pa.pertenece_ue = TRUE
             AND cl.estado = 'ACTIVO'
             AND REGEXP_LIKE(cl.numero_eori, '^[A-Z]{2}[A-Za-z0-9]{12,15}$', 'c')
            THEN 'CUMPLE' ELSE 'NO_CUMPLE'
       END AS dictamen_r_ue_008
  FROM ordenes_exportacion o
  JOIN clientes_ue cl ON cl.id_cliente = o.id_cliente
  JOIN paises      pa ON pa.id_pais    = cl.id_pais;


-- =====================================================================
-- DATOS SEMILLA (tarea tecnica de 2 SP del Sprint Backlog)
-- =====================================================================

INSERT INTO roles (nombre_rol, descripcion) VALUES
 ('ADMINISTRADOR',          'Administra usuarios, roles, permisos y reglas normativas; consulta auditoria'),
 ('GESTOR_EXPORTACIONES',   'Registra clientes, productores, fincas, lotes y ordenes de exportacion'),
 ('INSPECTOR_CALIDAD',      'Registra inspecciones, emite dictamenes y carga certificaciones'),
 ('CLIENTE_UE',             'Importador europeo con acceso de consulta a sus propias ordenes');

INSERT INTO permisos (codigo, modulo, descripcion) VALUES
 ('USUARIOS_GESTIONAR',   'SEGURIDAD', 'Crear, editar e inactivar usuarios y asignar roles'),
 ('AUDITORIA_CONSULTAR',  'SEGURIDAD', 'Consultar el rastro de auditoria del sistema'),
 ('REGLAS_ADMINISTRAR',   'NORMATIVA', 'Crear, editar, activar y desactivar reglas normativas UE'),
 ('CLIENTES_GESTIONAR',   'COMERCIAL', 'Crear y editar clientes importadores de la UE'),
 ('CLIENTES_CONSULTAR',   'COMERCIAL', 'Consultar y filtrar el listado de clientes'),
 ('PRODUCTOS_GESTIONAR',  'MAESTROS',  'Administrar el catalogo de productos y codigos HS'),
 ('PRODUCTORES_GESTIONAR','MAESTROS',  'Registrar productores y fincas con geolocalizacion'),
 ('LOTES_CREAR',          'LOTES',     'Registrar lotes de exportacion'),
 ('LOTES_TRAZABILIDAD',   'LOTES',     'Asociar fincas de origen y cerrar la trazabilidad'),
 ('LOTES_CONSULTAR',      'LOTES',     'Consultar lotes y su trazabilidad'),
 ('INSPECCION_REGISTRAR', 'CALIDAD',   'Registrar inspecciones y emitir dictamen'),
 ('CERTIFICADOS_CARGAR',  'CALIDAD',   'Cargar certificaciones del lote con su vigencia'),
 ('VALIDACION_EJECUTAR',  'NORMATIVA', 'Ejecutar el motor de validacion normativa sobre un lote'),
 ('ORDENES_GESTIONAR',    'COMERCIAL', 'Crear ordenes de exportacion y asignar lotes aprobados');

INSERT INTO roles_permisos (id_rol, id_permiso)
SELECT (SELECT id_rol FROM roles WHERE nombre_rol='ADMINISTRADOR'), id_permiso FROM permisos;

INSERT INTO roles_permisos (id_rol, id_permiso)
SELECT (SELECT id_rol FROM roles WHERE nombre_rol='GESTOR_EXPORTACIONES'), id_permiso FROM permisos
 WHERE codigo IN ('CLIENTES_GESTIONAR','CLIENTES_CONSULTAR','PRODUCTOS_GESTIONAR',
                  'PRODUCTORES_GESTIONAR','LOTES_CREAR','LOTES_TRAZABILIDAD',
                  'LOTES_CONSULTAR','VALIDACION_EJECUTAR','ORDENES_GESTIONAR');

INSERT INTO roles_permisos (id_rol, id_permiso)
SELECT (SELECT id_rol FROM roles WHERE nombre_rol='INSPECTOR_CALIDAD'), id_permiso FROM permisos
 WHERE codigo IN ('LOTES_CONSULTAR','INSPECCION_REGISTRAR','CERTIFICADOS_CARGAR');

INSERT INTO roles_permisos (id_rol, id_permiso)
SELECT (SELECT id_rol FROM roles WHERE nombre_rol='CLIENTE_UE'), id_permiso FROM permisos
 WHERE codigo IN ('LOTES_CONSULTAR');

-- Los 27 estados miembros de la UE mas Colombia (necesaria para CP-005)
INSERT INTO paises (codigo_iso2, nombre, pertenece_ue) VALUES
 ('AT','Austria',TRUE),      ('BE','Belgica',TRUE),      ('BG','Bulgaria',TRUE),
 ('HR','Croacia',TRUE),      ('CY','Chipre',TRUE),       ('CZ','Chequia',TRUE),
 ('DK','Dinamarca',TRUE),    ('EE','Estonia',TRUE),      ('FI','Finlandia',TRUE),
 ('FR','Francia',TRUE),      ('DE','Alemania',TRUE),     ('GR','Grecia',TRUE),
 ('HU','Hungria',TRUE),      ('IE','Irlanda',TRUE),      ('IT','Italia',TRUE),
 ('LV','Letonia',TRUE),      ('LT','Lituania',TRUE),     ('LU','Luxemburgo',TRUE),
 ('MT','Malta',TRUE),        ('NL','Paises Bajos',TRUE), ('PL','Polonia',TRUE),
 ('PT','Portugal',TRUE),     ('RO','Rumania',TRUE),      ('SK','Eslovaquia',TRUE),
 ('SI','Eslovenia',TRUE),    ('ES','Espana',TRUE),       ('SE','Suecia',TRUE),
 ('CO','Colombia',FALSE);

-- Usuarios de prueba del ambiente QA-RITECH (apartado 5.4).
-- ATENCION: los password_hash son marcadores; el backend debe generar
-- los hashes bcrypt reales. Tal como estan, nadie puede iniciar sesion.
INSERT INTO usuarios (id_rol, nombre_completo, email, password_hash, estado) VALUES
 ((SELECT id_rol FROM roles WHERE nombre_rol='ADMINISTRADOR'),
  'Felipe Ceron',               'admin@ritech.co',            '$2b$12$REEMPLAZAR_POR_HASH_REAL_ADMIN','ACTIVO'),
 ((SELECT id_rol FROM roles WHERE nombre_rol='GESTOR_EXPORTACIONES'),
  'Manuel Alejandro Villarreal','gestor@ritech.co',           '$2b$12$REEMPLAZAR_POR_HASH_REAL_GEST','ACTIVO'),
 ((SELECT id_rol FROM roles WHERE nombre_rol='INSPECTOR_CALIDAD'),
  'Andres Camilo Munoz Bolanos','inspector@ritech.co',        '$2b$12$REEMPLAZAR_POR_HASH_REAL_INSP','ACTIVO'),
 ((SELECT id_rol FROM roles WHERE nombre_rol='CLIENTE_UE'),
  'Importadora Europea SL',     'cliente@importadora-eu.com', '$2b$12$REEMPLAZAR_POR_HASH_REAL_CLIE','ACTIVO');

INSERT INTO productos (tipo, nombre_comercial, variedad, codigo_hs, unidad_medida) VALUES
 ('CAFE', 'Cafe Excelso Arabica',      'Caturra',    '0901', 'KG'),
 ('CAFE', 'Cafe Supremo Lavado',       'Castillo',   '0901', 'KG'),
 ('CACAO','Cacao en Grano Fino Aroma', 'Trinitario', '1801', 'KG'),
 ('CACAO','Cacao en Grano Corriente',  'Forastero',  '1801', 'KG');

-- El codigo 'FITOSANITARIO' es literal: sp_validar_lote lo busca por ese texto (R-UE-007)
INSERT INTO certificaciones (codigo, nombre, entidad_emisora, obligatoria_ue) VALUES
 ('FITOSANITARIO','Certificado Fitosanitario',       'ICA - Instituto Colombiano Agropecuario', TRUE),
 ('ORIGEN',       'Certificado de Origen',           'Camara de Comercio',                      TRUE),
 ('CALIDAD_FNC',  'Certificado de Calidad del Cafe', 'Federacion Nacional de Cafeteros',        FALSE),
 ('ORGANICO',     'Certificacion Organica UE',       'Organismo de control acreditado',         FALSE),
 ('FAIRTRADE',    'Certificacion de Comercio Justo', 'Fairtrade International',                 FALSE),
 ('GLOBALGAP',    'Certificacion GLOBALG.A.P.',      'Organismo certificador acreditado',       FALSE);

-- Las 8 reglas normativas: datos semilla del motor de validacion
INSERT INTO reglas_normativas_ue
  (codigo_regla, normativa_referencia, aplica_tipo, ambito, parametro, operador, valor_limite, mensaje_error) VALUES
 ('R-UE-001','Reglamento (UE) 2023/1115 - EUDR','AMBOS','LOTE','geolocalizacion_fincas','=','100',
  'Todas las fincas de origen deben tener geolocalizacion registrada (EUDR).'),
 ('R-UE-002','Reglamento (UE) 2023/1115 - EUDR','AMBOS','LOTE','poligono_si_area_mayor_4ha','=','true',
  'Las fincas con mas de 4 hectareas requieren poligono de geolocalizacion.'),
 ('R-UE-003','Reglamento (CE) 178/2002','AMBOS','LOTE','suma_aportes_finca','=','100',
  'La suma de los aportes de finca debe ser exactamente 100%.'),
 ('R-UE-004','Reglamento (UE) 2023/915','CAFE','LOTE','ocratoxina_a_cafe','<=','5.0',
  'La ocratoxina A supera el limite de 5,0 ug/kg del Reglamento (UE) 2023/915.'),
 ('R-UE-005','Reglamento (UE) 2023/915','CACAO','LOTE','aflatoxinas_cacao','<=','10.0',
  'La suma de aflatoxinas supera el limite de 10,0 ug/kg.'),
 ('R-UE-006','Practica comercial ICO / SCA','CAFE','LOTE','humedad_cafe_verde','<=','12.5',
  'La humedad excede el maximo de 12,5% admitido para cafe verde de exportacion.'),
 ('R-UE-007','Reglamento (UE) 2016/2031','AMBOS','LOTE','certificado_fitosanitario_vigente','=','true',
  'Certificado fitosanitario obligatorio ausente o vencido.'),
 ('R-UE-008','Reglamento (UE) 952/2013 - Codigo Aduanero','AMBOS','ORDEN','eori_importador_valido','=','true',
  'El importador debe tener un EORI valido y pertenecer a un pais miembro de la UE.');

INSERT INTO transportistas (nombre, tipo, contacto) VALUES
 ('Maersk Line',         'NAVIERA',   'bookings@maersk-co.example'),
 ('Hapag-Lloyd',         'NAVIERA',   'export@hapag-co.example'),
 ('Avianca Cargo',       'AEREA',     'cargo@avianca-co.example'),
 ('Transportes Andinos', 'TERRESTRE', 'despachos@tandinos.example');

INSERT INTO clientes_ue
  (razon_social, numero_eori, nit_vat, id_pais, ciudad, direccion, email_contacto) VALUES
 ('Europa Coffee Importers GmbH', 'DE123456789012345', 'DE811234567',
  (SELECT id_pais FROM paises WHERE codigo_iso2 = 'DE'),
  'Hamburgo', 'Speicherstadt 14', 'compras@europacoffee-eu.example'),
 ('Cacao Fino Iberica SL',        'ES987654321098765', 'ESB12345678',
  (SELECT id_pais FROM paises WHERE codigo_iso2 = 'ES'),
  'Valencia', 'Avenida del Puerto 102', 'importacion@cacaofino-eu.example');

-- Cierre de la relacion 1:0..1: la cuenta del portal apunta a su ficha comercial
UPDATE usuarios
   SET id_cliente = (SELECT id_cliente FROM clientes_ue
                      WHERE numero_eori = 'DE123456789012345')
 WHERE email = 'cliente@importadora-eu.com';


-- =====================================================================
-- JUEGO DE DEMOSTRACION: un lote que llega a APROBADO + CUMPLE
-- (es el Sprint Goal del informe)
-- =====================================================================

-- El rastro de auditoria del propio juego de datos queda a nombre del gestor.
SET @app_id_usuario := (SELECT id_usuario FROM usuarios WHERE email = 'gestor@ritech.co');
SET @app_ip_origen  := '127.0.0.1';

INSERT INTO productores (nit_o_cedula, nombre, municipio, departamento, telefono) VALUES
 ('900123456-7','Asociacion Cafetera del Cauca','Popayan','Cauca','+57 602 8201234');

SET @productor := (SELECT id_productor FROM productores WHERE nit_o_cedula = '900123456-7');

INSERT INTO fincas (id_productor, nombre_finca, area_hectareas, latitud, longitud,
                    poligono_geojson, fecha_verificacion_deforestacion) VALUES
 (@productor,'La Esperanza', 3.50, 2.441000, -76.606000, NULL,
  DATE_SUB(CURDATE(), INTERVAL 100 DAY)),
 (@productor,'El Mirador',   6.20, 2.455000, -76.590000,
  '{"type":"Polygon","coordinates":[[[-76.591,2.454],[-76.589,2.454],[-76.589,2.456],[-76.591,2.456],[-76.591,2.454]]]}',
  DATE_SUB(CURDATE(), INTERVAL 100 DAY)),
 (@productor,'Buenavista',   2.80, 2.430000, -76.615000, NULL,
  DATE_SUB(CURDATE(), INTERVAL 96 DAY));

INSERT INTO lotes_exportacion
  (codigo_lote, id_producto, id_usuario_registra, peso_neto_kg, peso_bruto_kg, fecha_cosecha) VALUES
 ('CAF-2026-0001',
  (SELECT id_producto FROM productos WHERE nombre_comercial = 'Cafe Excelso Arabica'),
  (SELECT id_usuario  FROM usuarios  WHERE email = 'gestor@ritech.co'),
  19200.00, 19500.00, DATE_SUB(CURDATE(), INTERVAL 91 DAY));

SET @lote := (SELECT id_lote FROM lotes_exportacion WHERE codigo_lote = 'CAF-2026-0001');

INSERT INTO lotes_fincas (id_lote, id_finca, porcentaje_aporte, kg_aportados) VALUES
 (@lote, (SELECT id_finca FROM fincas WHERE nombre_finca='La Esperanza'), 50.00, 9600.00),
 (@lote, (SELECT id_finca FROM fincas WHERE nombre_finca='El Mirador'),   30.00, 5760.00),
 (@lote, (SELECT id_finca FROM fincas WHERE nombre_finca='Buenavista'),   20.00, 3840.00);

CALL sp_cerrar_trazabilidad(@lote);

INSERT INTO lotes_certificaciones
  (id_lote, id_certificacion, numero_documento, fecha_emision, fecha_vencimiento, url_soporte) VALUES
 (@lote, (SELECT id_certificacion FROM certificaciones WHERE codigo='FITOSANITARIO'),
  'FITO-2026-8891',
  DATE_SUB(CURDATE(), INTERVAL 65 DAY),
  DATE_ADD(CURDATE(), INTERVAL 115 DAY),
  '/storage/certificados/fito-2026-8891.pdf');

INSERT INTO inspecciones_calidad
  (id_lote, id_usuario_inspector, fecha_inspeccion, humedad_porcentaje, defectos_por_300g,
   puntaje_taza, ocratoxina_ppb, resultado, observaciones) VALUES
 (@lote, (SELECT id_usuario FROM usuarios WHERE email='inspector@ritech.co'),
  DATE_SUB(CURDATE(), INTERVAL 50 DAY), 11.20, 5, 84.50, 2.10, 'APROBADO',
  'Lote conforme en humedad, defectos, perfil de taza y contaminantes.');

INSERT INTO ordenes_exportacion
  (numero_orden, id_cliente, id_usuario_gestor, fecha_orden, incoterm, moneda, estado_orden) VALUES
 ('ORD-2026-0001',
  (SELECT id_cliente FROM clientes_ue WHERE numero_eori='DE123456789012345'),
  (SELECT id_usuario FROM usuarios    WHERE email='gestor@ritech.co'),
  DATE_SUB(CURDATE(), INTERVAL 39 DAY), 'FOB', 'EUR', 'BORRADOR');

SET @orden := (SELECT id_orden FROM ordenes_exportacion WHERE numero_orden = 'ORD-2026-0001');

-- La validacion normativa es requisito de trg_ordlot_bi y se ejecuta antes de asignar.
CALL sp_validar_lote(@lote);

-- trg_ordlot_ai calcula valor_total y pasa el lote a ASIGNADO.
INSERT INTO ordenes_lotes (id_orden, id_lote, kg_asignados, precio_unitario) VALUES
 (@orden, @lote, 19200.00, 4.8500);

UPDATE ordenes_exportacion SET estado_orden = 'CONFIRMADA' WHERE id_orden = @orden;

INSERT INTO envios (id_orden, id_transportista, numero_contenedor, booking_bl,
                    puerto_origen, puerto_destino, fecha_estimada_zarpe, fecha_estimada_arribo) VALUES
 (@orden, (SELECT id_transportista FROM transportistas WHERE nombre='Maersk Line'),
  'MSKU1234567', 'BK-2026-55012', 'Buenaventura', 'Hamburgo',
  DATE_SUB(CURDATE(), INTERVAL 35 DAY), DATE_SUB(CURDATE(), INTERVAL 12 DAY));

SET @envio := (SELECT id_envio FROM envios WHERE booking_bl = 'BK-2026-55012');

INSERT INTO seguimiento_envios (id_envio, fecha_evento, ubicacion, estado, observacion) VALUES
 (@envio, DATE_SUB(NOW(), INTERVAL 38 DAY), 'Bodega Popayan',      'PROGRAMADO',
  'Lote consolidado y precintado'),
 (@envio, DATE_SUB(NOW(), INTERVAL 36 DAY), 'Puerto Buenaventura', 'EN_PUERTO',
  'Contenedor en patio, pendiente de embarque');

INSERT INTO documentos_exportacion (id_orden, tipo_documento, numero, url_archivo, fecha_emision) VALUES
 (@orden, 'FACTURA_COMERCIAL',  'FC-2026-0001',   '/storage/docs/fc-2026-0001.pdf',
  DATE_SUB(CURDATE(), INTERVAL 40 DAY)),
 (@orden, 'LISTA_EMPAQUE',      'LE-2026-0001',   '/storage/docs/le-2026-0001.pdf',
  DATE_SUB(CURDATE(), INTERVAL 40 DAY)),
 (@orden, 'CERT_FITOSANITARIO', 'FITO-2026-8891', '/storage/docs/fito-2026-8891.pdf',
  DATE_SUB(CURDATE(), INTERVAL 65 DAY));


-- =====================================================================
-- VERIFICACION DEL MODELO (bloque "pruebas de base de datos", apartado 5.3)
-- =====================================================================

SELECT VERSION() AS version_mysql;   -- debe ser 8.0.16 o superior

SELECT COUNT(*) AS total_tablas      -- esperado: 23
  FROM information_schema.tables
 WHERE table_schema = 'db_ritech_exports' AND table_type = 'BASE TABLE';

-- Esperado: 26 = las 16 relaciones 1:N del apartado 2.3
--              + 8 de las cuatro tablas puente (2 cada una)
--              + 1 de la relacion 1:0..1 usuarios -> clientes_ue
--              + 1 de reglas_normativas_ue -> productos, que el apartado 2.2
--                declara como "FK nullable" pero el 2.3 no lista como relacion.
SELECT COUNT(*) AS total_llaves_foraneas
  FROM information_schema.referential_constraints
 WHERE constraint_schema = 'db_ritech_exports';

SELECT COUNT(*) AS total_checks
  FROM information_schema.check_constraints
 WHERE constraint_schema = 'db_ritech_exports';

SELECT COUNT(*) AS total_disparadores  -- esperado: 19
  FROM information_schema.triggers
 WHERE trigger_schema = 'db_ritech_exports';

SELECT COUNT(*) AS total_vistas          -- esperado: 3
  FROM information_schema.views
 WHERE table_schema = 'db_ritech_exports';

SELECT table_name, engine              -- debe devolver 0 filas
  FROM information_schema.tables
 WHERE table_schema = 'db_ritech_exports' AND table_type = 'BASE TABLE'
   AND engine <> 'InnoDB';

-- Detalle regla por regla de la ultima validacion del lote 1
SELECT r.codigo_regla, r.aplica_tipo, r.parametro,
       v.operador_aplicado, v.valor_limite_aplicado,
       v.valor_evaluado, v.resultado
  FROM validaciones_lote v
  JOIN reglas_normativas_ue r ON r.id_regla = v.id_regla
 WHERE v.id_lote = 1
   AND v.fecha_ejecucion = (SELECT MAX(fecha_ejecucion) FROM validaciones_lote WHERE id_lote = 1)
 ORDER BY r.codigo_regla;

SELECT * FROM v_dictamen_lote;
SELECT * FROM v_trazabilidad_lote;
SELECT * FROM v_cumplimiento_importador;


-- =====================================================================
-- PRUEBAS NEGATIVAS - TODAS DEBEN FALLAR
-- Estan comentadas para que el script corra de una sola pasada.
-- Descomenta y ejecuta UNA POR UNA: cada una debe producir error.
-- =====================================================================
/*
-- CP-014: peso bruto menor que el neto -> chk_pesos
INSERT INTO lotes_exportacion (codigo_lote, id_producto, id_usuario_registra,
       peso_neto_kg, peso_bruto_kg, fecha_cosecha)
VALUES ('CAF-2026-9901', 1, 2, 19200.00, 18000.00, '2026-08-10');

-- CP-015: fecha de cosecha futura -> trg_lotes_fecha_bi
INSERT INTO lotes_exportacion (codigo_lote, id_producto, id_usuario_registra,
       peso_neto_kg, peso_bruto_kg, fecha_cosecha)
VALUES ('CAF-2026-9902', 1, 2, 1000.00, 1050.00, '2027-12-15');

-- CP-011: latitud fuera de rango -> chk_latitud
INSERT INTO fincas (id_productor, nombre_finca, area_hectareas, latitud, longitud)
VALUES (1, 'Finca Invalida', 2.00, 95.000000, -76.600000);

-- CP-010: mas de 4 hectareas sin poligono -> chk_poligono_eudr
INSERT INTO fincas (id_productor, nombre_finca, area_hectareas, latitud, longitud)
VALUES (1, 'Finca Grande', 6.20, 2.455000, -76.590000);

-- CP-012: NIT de productor duplicado -> uq_productores_nit
INSERT INTO productores (nit_o_cedula, nombre, municipio, departamento)
VALUES ('900123456-7', 'Otro Productor', 'Timbio', 'Cauca');

-- CP-018: la misma finca dos veces en un lote -> trg_lotfin_bi (y la PK compuesta detras)
INSERT INTO lotes_fincas (id_lote, id_finca, porcentaje_aporte, kg_aportados)
VALUES (1, 1, 20.00, 3840.00);

-- CP-019: kilos que no corresponden al porcentaje -> trg_lotfin_bi
-- Se usa un lote nuevo porque el lote 1 ya tiene su trazabilidad cerrada al 100%.
INSERT INTO lotes_exportacion (codigo_lote, id_producto, id_usuario_registra,
       peso_neto_kg, peso_bruto_kg, fecha_cosecha)
VALUES ('CAF-2026-9903', 1, 2, 10000.00, 10200.00, '2026-08-12');
INSERT INTO lotes_fincas (id_lote, id_finca, porcentaje_aporte, kg_aportados)
VALUES (LAST_INSERT_ID(), 1, 50.00, 12000.00);   -- lo esperado eran 5.000 kg

-- CP-024: lote RECHAZADO sin observacion -> chk_resultado_insp (el bug corregido)
INSERT INTO inspecciones_calidad (id_lote, id_usuario_inspector, resultado, observaciones)
VALUES (1, 3, 'RECHAZADO', NULL);

-- CP-004: EORI duplicado -> uq_clientes_eori
INSERT INTO clientes_ue (razon_social, numero_eori, id_pais, ciudad, email_contacto)
VALUES ('Duplicada GmbH', 'DE123456789012345',
        (SELECT id_pais FROM paises WHERE codigo_iso2='DE'), 'Berlin', 'x@dup-eu.example');

-- CP-005: importador de pais no miembro de la UE -> trg_clientes_pais_bi
INSERT INTO clientes_ue (razon_social, numero_eori, id_pais, ciudad, email_contacto)
VALUES ('Importadora Bogota SAS', 'CO123456789012345',
        (SELECT id_pais FROM paises WHERE codigo_iso2='CO'), 'Bogota', 'x@no-ue.example');

-- CP-006: EORI con formato invalido -> chk_eori_formato
INSERT INTO clientes_ue (razon_social, numero_eori, id_pais, ciudad, email_contacto)
VALUES ('Formato Malo GmbH', '123',
        (SELECT id_pais FROM paises WHERE codigo_iso2='DE'), 'Munich', 'x@mal-eu.example');

-- CP-029: vencimiento anterior a la emision -> chk_vigencia_cert
INSERT INTO lotes_certificaciones (id_lote, id_certificacion, numero_documento,
       fecha_emision, fecha_vencimiento)
VALUES (1, 2, 'ORIG-2026-001', '2026-09-10', '2026-09-01');

-- CP-028: certificado ya vencido -> trg_lotcert_bi
INSERT INTO lotes_certificaciones (id_lote, id_certificacion, numero_documento,
       fecha_emision, fecha_vencimiento)
VALUES (1, 3, 'FNC-2025-777', '2025-01-01', '2026-06-01');

-- CP-025: asignar un lote que no esta en APROBADO -> trg_ordlot_bi
-- Tras la demo el lote 1 quedo en ASIGNADO, asi que el trigger lo rechaza
-- por estado antes incluso de revisar los kilogramos disponibles.
INSERT INTO ordenes_lotes (id_orden, id_lote, kg_asignados, precio_unitario)
VALUES (1, 1, 100.00, 4.8500);

-- CP-008: borrar un cliente con ordenes asociadas -> fk_ordenes_cliente RESTRICT
DELETE FROM clientes_ue WHERE id_cliente = 1;

-- ---------------------------------------------------------------------
-- Casos de la revision B. Cada uno reproduce un defecto que la version
-- anterior aceptaba en silencio.
-- ---------------------------------------------------------------------

-- CP-030 (C-01): codigo de pais en minuscula -> chk_codigo_iso2 con match 'c'
INSERT INTO paises (codigo_iso2, nombre, pertenece_ue) VALUES ('zz','Pais Falso',TRUE);

-- CP-031 (C-01): EORI en minuscula -> chk_eori_formato con match 'c'
INSERT INTO clientes_ue (razon_social, numero_eori, id_pais, ciudad, email_contacto)
VALUES ('Minuscula GmbH','de999888777666',
        (SELECT id_pais FROM paises WHERE codigo_iso2='DE'),'Koln','x@min-eu.example');

-- CP-032 (C-03): UPDATE que lleva la suma de aportes al 140% -> trg_lotfin_bu
UPDATE lotes_fincas SET porcentaje_aporte = 90.00, kg_aportados = 17280.00
 WHERE id_lote = 1 AND id_finca = 1;

-- CP-033 (C-04): UPDATE que excede el peso neto del lote -> trg_ordlot_bu
UPDATE ordenes_lotes SET kg_asignados = 999999.00 WHERE id_lote = 1;

-- CP-034 (C-09): estado fuera del dominio en la bitacora -> chk_estado_seguimiento
INSERT INTO seguimiento_envios (id_envio, ubicacion, estado)
VALUES (1,'Oficina','cualquier_cosa');

-- CP-035 (C-12): contenedor que no sigue ISO 6346 -> chk_contenedor_iso6346
UPDATE envios SET numero_contenedor = '123' WHERE id_envio = 1;

-- CP-036 (C-12): segundo envio con el mismo BL -> uq_envios_booking
INSERT INTO envios (id_orden, id_transportista, booking_bl, puerto_origen, puerto_destino)
VALUES (1, 1, 'BK-2026-55012', 'Cartagena', 'Rotterdam');
*/

/*
-- ---------------------------------------------------------------------
-- Comprobaciones de la revision B que deben PASAR. Tambien comentadas:
-- ejecutar sobre la base recien creada.
-- ---------------------------------------------------------------------

-- C-02: dos corridas seguidas no se mezclan. reglas_evaluadas debe seguir en 6.
CALL sp_validar_lote(1);
CALL sp_validar_lote(1);
SELECT reglas_evaluadas, dictamen FROM v_dictamen_lote WHERE id_lote = 1;
SELECT COUNT(DISTINCT fecha_ejecucion) AS corridas, COUNT(*) AS filas
  FROM validaciones_lote WHERE id_lote = 1;

-- C-05: valor_total lo mantiene la base, no el backend.
SELECT o.valor_total AS guardado,
       (SELECT SUM(kg_asignados * precio_unitario)
          FROM ordenes_lotes WHERE id_orden = o.id_orden) AS recalculado
  FROM ordenes_exportacion o WHERE o.id_orden = 1;

-- C-06: una humedad real del 22% ya se puede registrar y el lote se rechaza.
INSERT INTO inspecciones_calidad (id_lote, id_usuario_inspector, humedad_porcentaje,
       resultado, observaciones)
VALUES (1,(SELECT id_usuario FROM usuarios WHERE email='inspector@ritech.co'),22.00,
        'RECHAZADO','Humedad muy por encima del maximo admitido para exportacion.');

-- C-07: sin @app_id_usuario el rastro queda sin autor en vez de imputarlo al admin.
SET @app_id_usuario := NULL;
UPDATE clientes_ue SET ciudad = 'Bremen' WHERE id_cliente = 1;
SELECT tabla_afectada, accion, id_usuario FROM auditoria ORDER BY id_auditoria DESC LIMIT 1;
*/

-- =====================================================================
-- Fin del script. db_ritech_exports, 23 tablas, 26 FK, 41 CHECK,
-- 19 disparadores, 2 procedimientos, 3 vistas y datos semilla.
-- Conteos verificados contra information_schema en MySQL 8.0.46.
-- =====================================================================
