package com.ritech.exports.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Prueba de punta a punta contra MySQL REAL, con los triggers y procedimientos del script.
 *
 * Solo corre si existe la variable RITECH_MYSQL_URL (p. ej. jdbc:mysql://localhost:3306).
 * Crea desde cero la base db_ritech_pruebas; nunca toca db_ritech_exports.
 *
 *   $env:RITECH_MYSQL_URL="jdbc:mysql://localhost:3306"; $env:RITECH_MYSQL_PASSWORD="clave"
 *   ./mvnw test -Dtest=FlujoExportacionMySqlIT
 */
@SpringBootTest(properties = {
        "ritech.semilla.password-inicial=" + FlujoExportacionMySqlIT.CLAVE,
        // Una sola conexion: asi se puede comprobar que las variables de auditoria quedan limpias.
        "spring.datasource.hikari.maximum-pool-size=1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RITECH_MYSQL_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlujoExportacionMySqlIT {

    static final String CLAVE = "Prueba-Integracion-2026";
    private static final String OPCIONES = "allowPublicKeyRetrieval=true&useSSL=false"
            + "&connectionTimeZone=LOCAL&forceConnectionTimeZoneToSession=true";

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry r) throws Exception {
        String servidor = System.getenv("RITECH_MYSQL_URL").replaceAll("/+$", "");
        String usuario = System.getenv().getOrDefault("RITECH_MYSQL_USER", "root");
        String clave = System.getenv().getOrDefault("RITECH_MYSQL_PASSWORD", "");
        ScriptMySql.cargar(servidor + "/?" + OPCIONES, usuario, clave);
        r.add("spring.datasource.url", () -> servidor + "/" + ScriptMySql.BASE_PRUEBAS + "?" + OPCIONES);
        r.add("spring.datasource.username", () -> usuario);
        r.add("spring.datasource.password", () -> clave);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient jdbc;

    private final LocalDate hoy = LocalDate.now();
    private String admin;
    private String gestor;
    private String inspector;
    private String cliente;
    private int idGestor;
    private int idInspector;
    private int finca1;
    private int finca2;
    private int lote;
    private int loteAjeno;
    private int orden;
    private int envio;

    // ---------- apoyo ----------

    private String llamar(MockHttpServletRequestBuilder b, String token, String cuerpo, ResultMatcher esperado)
            throws Exception {
        if (token != null) {
            b.header("Authorization", token);
        }
        if (cuerpo != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(b).andExpect(esperado).andReturn().getResponse().getContentAsString();
    }

    private String entrar(String email) throws Exception {
        String r = llamar(post("/api/auth/login"), null,
                "{\"email\":\"" + email + "\",\"password\":\"" + CLAVE + "\"}", status().isOk());
        return "Bearer " + JsonPath.read(r, "$.token");
    }

    private static int id(String json, String ruta) {
        return ((Number) JsonPath.read(json, ruta)).intValue();
    }

    // ---------- flujo ----------

    @Test
    @Order(1)
    void losUsuariosSemillaPuedenEntrar() throws Exception {
        admin = entrar("admin@ritech.co");
        gestor = entrar("gestor@ritech.co");
        inspector = entrar("inspector@ritech.co");
        cliente = entrar("cliente@importadora-eu.com");
        idGestor = id(llamar(get("/api/auth/yo"), gestor, null, status().isOk()), "$.idUsuario");
        idInspector = id(llamar(get("/api/auth/yo"), inspector, null, status().isOk()), "$.idUsuario");

        llamar(post("/api/auth/login"), null, "{\"email\":\"admin@ritech.co\",\"password\":\"mala\"}",
                status().isUnauthorized());
    }

    @Test
    @Order(2)
    void elGestorRegistraElOrigenYElLote() throws Exception {
        int productor = id(llamar(post("/api/productores"), gestor, """
                {"nitOCedula":"901555444-2","nombre":"Cooperativa Integracion","municipio":"Pitalito",
                 "departamento":"Huila"}""", status().isCreated()), "$.idProductor");
        String finca = """
                {"nombreFinca":"%s","areaHectareas":%s,"latitud":1.85,"longitud":-76.05,
                 "fechaVerificacionDeforestacion":"%s"}""";
        finca1 = id(llamar(post("/api/productores/" + productor + "/fincas"), gestor,
                finca.formatted("La Primavera", "3.2", hoy.minusDays(30)), status().isCreated()), "$.idFinca");
        finca2 = id(llamar(post("/api/productores/" + productor + "/fincas"), gestor,
                finca.formatted("Santa Ana", "2.0", hoy.minusDays(30)), status().isCreated()), "$.idFinca");

        String l = llamar(post("/api/lotes"), gestor, """
                {"idProducto":1,"pesoNetoKg":10000,"pesoBrutoKg":10150,"fechaCosecha":"%s"}"""
                .formatted(hoy.minusDays(20)), status().isCreated());
        lote = id(l, "$.idLote");
        assertThat((String) JsonPath.read(l, "$.estadoLote")).isEqualTo("REGISTRADO");
        assertThat((String) JsonPath.read(l, "$.codigoLote")).startsWith("CAF-" + hoy.getYear() + "-");

        String a = llamar(post("/api/lotes/" + lote + "/fincas"), gestor,
                "{\"idFinca\":" + finca1 + ",\"porcentajeAporte\":60}", status().isCreated());
        assertThat(((Number) JsonPath.read(a, "$.kgAportados")).doubleValue()).isEqualTo(6000.0);
        llamar(post("/api/lotes/" + lote + "/fincas"), gestor,
                "{\"idFinca\":" + finca2 + ",\"porcentajeAporte\":40}", status().isCreated());
    }

    @Test
    @Order(3)
    void losTriggersDelOrigenResponden422ConSuMensaje() throws Exception {
        String dup = llamar(post("/api/lotes/" + lote + "/fincas"), gestor,
                "{\"idFinca\":" + finca1 + ",\"porcentajeAporte\":5}", status().isUnprocessableEntity());
        assertThat((String) JsonPath.read(dup, "$.mensaje")).isEqualTo("La finca ya se encuentra asociada a este lote.");

        String kg = llamar(post("/api/lotes"), gestor, """
                {"idProducto":1,"pesoNetoKg":1000,"pesoBrutoKg":1010,"fechaCosecha":"%s"}"""
                .formatted(hoy.minusDays(5)), status().isCreated());
        loteAjeno = id(kg, "$.idLote");
        String malKg = llamar(post("/api/lotes/" + loteAjeno + "/fincas"), gestor,
                "{\"idFinca\":" + finca1 + ",\"porcentajeAporte\":50,\"kgAportados\":900}",
                status().isUnprocessableEntity());
        assertThat((String) JsonPath.read(malKg, "$.mensaje")).contains("kg aportados no corresponden");
    }

    @Test
    @Order(4)
    void laTrazabilidadQuedaCerrada() throws Exception {
        String r = llamar(post("/api/lotes/" + lote + "/cerrar-trazabilidad"), gestor, null, status().isOk());
        assertThat((String) JsonPath.read(r, "$.mensaje")).isEqualTo("Trazabilidad completa");

        llamar(post("/api/lotes/" + loteAjeno + "/cerrar-trazabilidad"), gestor, null,
                status().isUnprocessableEntity());
    }

    @Test
    @Order(5)
    void elInspectorCargaElFitosanitarioYApruebaElLote() throws Exception {
        String catalogo = llamar(get("/api/certificaciones"), inspector, null, status().isOk());
        List<Integer> fito = JsonPath.read(catalogo, "$[?(@.codigo == 'FITOSANITARIO')].idCertificacion");
        llamar(post("/api/lotes/" + lote + "/certificados"), inspector, """
                {"idCertificacion":%d,"numeroDocumento":"FITO-IT-0001","fechaEmision":"%s","fechaVencimiento":"%s"}"""
                .formatted(fito.get(0), hoy.minusDays(2), hoy.plusDays(90)), status().isCreated());

        String i = llamar(post("/api/lotes/" + lote + "/inspecciones"), inspector, """
                {"resultado":"APROBADO","humedadPorcentaje":11.5,"defectosPor300g":4,"puntajeTaza":85,
                 "ocratoxinaPpb":1.8}""", status().isCreated());
        assertThat((String) JsonPath.read(i, "$.estadoLote")).as("lo movio trg_insp_estado_ai").isEqualTo("APROBADO");
    }

    @Test
    @Order(6)
    void elMotorDeValidacionDaCumple() throws Exception {
        String d = llamar(post("/api/lotes/" + lote + "/validar"), gestor, null, status().isOk());

        assertThat((String) JsonPath.read(d, "$.dictamen")).isEqualTo("CUMPLE");
        assertThat((Integer) JsonPath.read(d, "$.reglasEvaluadas")).as("6 reglas de cafe de ambito LOTE").isEqualTo(6);
        List<String> resultados = JsonPath.read(d, "$.detalle[*].resultado");
        assertThat(resultados).hasSize(6).containsOnly("CUMPLE");
    }

    @Test
    @Order(7)
    void laOrdenRecibeElLoteYLaBaseCalculaElValor() throws Exception {
        String o = llamar(post("/api/ordenes"), gestor, "{\"idCliente\":1,\"incoterm\":\"FOB\"}", status().isCreated());
        orden = id(o, "$.idOrden");

        String d = llamar(post("/api/ordenes/" + orden + "/lotes"), gestor,
                "{\"idLote\":" + lote + ",\"precioUnitario\":5.10}", status().isCreated());

        assertThat(((Number) JsonPath.read(d, "$.orden.valorTotal")).doubleValue())
                .as("trg_ordlot_ai: 10000 kg x 5,10").isEqualTo(51000.0);
        String l = llamar(get("/api/lotes/" + lote), gestor, null, status().isOk());
        assertThat((String) JsonPath.read(l, "$.estadoLote")).isEqualTo("ASIGNADO");

        llamar(post("/api/ordenes/" + orden + "/lotes"), gestor,
                "{\"idLote\":" + loteAjeno + ",\"precioUnitario\":5}", status().isUnprocessableEntity());
    }

    @Test
    @Order(8)
    void laAuditoriaRegistraAlAutorRealDeCadaCambio() throws Exception {
        String a = llamar(get("/api/auditoria?tabla=lotes_exportacion&tamano=200"), admin, null, status().isOk());
        String filtro = "$.content[?(@.idRegistro == '" + lote + "' && @.accion == '%s')].idUsuario";

        List<Integer> altas = JsonPath.read(a, filtro.formatted("INSERT"));
        assertThat(altas).as("el gestor creo el lote").containsExactly(idGestor);

        List<Integer> cambios = JsonPath.read(a, filtro.formatted("UPDATE"));
        assertThat(cambios).as("el inspector lo aprobo y el gestor lo asigno")
                .contains(idInspector, idGestor)
                .doesNotContainNull();

        Map<String, Object> sesion = jdbc.sql("select @app_id_usuario as u, @app_ip_origen as ip").query().singleRow();
        assertThat(sesion.get("u")).as("la conexion del pool queda sin autor").isNull();
        assertThat(sesion.get("ip")).isNull();
    }

    @Test
    @Order(9)
    void elClienteSoloVeLoQueCompro() throws Exception {
        String lotes = llamar(get("/api/lotes"), cliente, null, status().isOk());
        List<Integer> ids = JsonPath.read(lotes, "$[*].idLote");
        assertThat(ids).contains(lote).doesNotContain(loteAjeno);

        llamar(get("/api/lotes/" + loteAjeno), cliente, null, status().isNotFound());

        String t = llamar(get("/api/lotes/" + lote + "/trazabilidad"), cliente, null, status().isOk());
        assertThat((Boolean) JsonPath.read(t, "$.completa")).isTrue();
        assertThat((List<?>) JsonPath.read(t, "$.origen")).hasSize(2);
    }

    @Test
    @Order(10)
    void elEnvioZarpaYElLoteQuedaExportado() throws Exception {
        llamar(patch("/api/ordenes/" + orden + "/estado"), gestor, "{\"estado\":\"CONFIRMADA\"}", status().isOk());
        String e = llamar(post("/api/ordenes/" + orden + "/envios"), gestor, """
                {"idTransportista":1,"numeroContenedor":"MSCU7654321","bookingBl":"BK-IT-%d",
                 "puertoOrigen":"Buenaventura","puertoDestino":"Hamburgo",
                 "fechaEstimadaZarpe":"%s","fechaEstimadaArribo":"%s"}"""
                .formatted(System.nanoTime(), hoy.plusDays(1), hoy.plusDays(20)), status().isCreated());
        envio = id(e, "$.envio.idEnvio");

        llamar(post("/api/envios/" + envio + "/seguimiento"), gestor,
                "{\"ubicacion\":\"Oceano Pacifico\",\"estado\":\"EN_TRANSITO\"}", status().isCreated());

        String o = llamar(get("/api/ordenes/" + orden), gestor, null, status().isOk());
        assertThat((String) JsonPath.read(o, "$.orden.estadoOrden")).isEqualTo("EN_TRANSITO");
        String l = llamar(get("/api/lotes/" + lote), gestor, null, status().isOk());
        assertThat((String) JsonPath.read(l, "$.estadoLote")).isEqualTo("EXPORTADO");

        String vista = llamar(get("/api/envios/" + envio), cliente, null, status().isOk());
        assertThat((List<?>) JsonPath.read(vista, "$.seguimiento")).hasSize(2);
    }
}
