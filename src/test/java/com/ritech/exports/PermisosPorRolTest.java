package com.ritech.exports;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ritech.exports.comercial.ClienteServicio;
import com.ritech.exports.comercial.ClienteUe;
import com.ritech.exports.comun.ApiException;
import com.ritech.exports.config.DatosIniciales;
import com.ritech.exports.lotes.InspeccionServicio;
import com.ritech.exports.ordenes.OrdenServicio;
import com.ritech.exports.seguridad.JwtServicio;
import com.ritech.exports.usuarios.Permiso;
import com.ritech.exports.usuarios.Rol;
import com.ritech.exports.usuarios.Usuario;
import com.ritech.exports.usuarios.UsuarioRepositorio;
import com.ritech.exports.usuarios.UsuarioServicio;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Cada rol con los permisos exactos que le da el script (roles_permisos). Las peticiones llevan un
 * JWT real y pasan por el filtro y la cadena de Spring Security; solo los servicios son simulados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermisosPorRolTest {

    private static final String[] PERMISOS_GESTOR = {"CLIENTES_GESTIONAR", "CLIENTES_CONSULTAR",
            "PRODUCTOS_GESTIONAR", "PRODUCTORES_GESTIONAR", "LOTES_CREAR", "LOTES_TRAZABILIDAD",
            "LOTES_CONSULTAR", "VALIDACION_EJECUTAR", "ORDENES_GESTIONAR"};
    private static final String[] PERMISOS_INSPECTOR = {"LOTES_CONSULTAR", "INSPECCION_REGISTRAR",
            "CERTIFICADOS_CARGAR"};
    private static final String[] PERMISOS_CLIENTE = {"LOTES_CONSULTAR"};
    private static final String[] PERMISOS_ADMIN = {"USUARIOS_GESTIONAR", "AUDITORIA_CONSULTAR",
            "REGLAS_ADMINISTRAR", "CLIENTES_GESTIONAR", "CLIENTES_CONSULTAR", "PRODUCTOS_GESTIONAR",
            "PRODUCTORES_GESTIONAR", "LOTES_CREAR", "LOTES_TRAZABILIDAD", "LOTES_CONSULTAR",
            "INSPECCION_REGISTRAR", "CERTIFICADOS_CARGAR", "VALIDACION_EJECUTAR", "ORDENES_GESTIONAR"};

    private static final String CLIENTE_VALIDO = """
            {"razonSocial":"Europa Coffee GmbH","numeroEori":"DE123456789012345","idPais":11,
             "ciudad":"Hamburgo","emailContacto":"compras@europa.example"}""";

    @MockitoBean
    DatosIniciales datosIniciales;
    @MockitoBean
    UsuarioRepositorio usuarios;
    @MockitoBean
    ClienteServicio clientes;
    @MockitoBean
    UsuarioServicio usuarioServicio;
    @MockitoBean
    OrdenServicio ordenes;
    @MockitoBean
    InspeccionServicio inspecciones;

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtServicio jwt;

    private String admin;
    private String gestor;
    private String inspector;
    private String cliente;

    private String token(int id, String rol, Integer idCliente, String estado, String... permisos) {
        Rol r = new Rol();
        r.setNombreRol(rol);
        r.setPermisos(Arrays.stream(permisos).map(c -> {
            Permiso p = new Permiso();
            p.setCodigo(c);
            return p;
        }).collect(Collectors.toCollection(HashSet::new)));
        Usuario u = new Usuario();
        u.setIdUsuario(id);
        u.setEmail(rol.toLowerCase() + "@ritech.co");
        u.setRol(r);
        u.setIdCliente(idCliente);
        u.setEstado(estado);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwt.emitir(u);
    }

    @BeforeEach
    void usuariosDelScript() {
        admin = token(1, Rol.ADMINISTRADOR, null, "ACTIVO", PERMISOS_ADMIN);
        gestor = token(2, Rol.GESTOR, null, "ACTIVO", PERMISOS_GESTOR);
        inspector = token(3, Rol.INSPECTOR, null, "ACTIVO", PERMISOS_INSPECTOR);
        cliente = token(4, Rol.CLIENTE_UE, 1, "ACTIVO", PERMISOS_CLIENTE);
        when(clientes.crear(any())).thenReturn(new ClienteUe());
        when(usuarioServicio.listar()).thenReturn(List.of());
        when(ordenes.buscar(any(), any())).thenReturn(List.of());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String token, String cuerpo) {
        return b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    // ---------- Clientes UE ----------

    @Test
    void elGestorRegistraClientesYElInspectorNo() throws Exception {
        mvc.perform(json(post("/api/clientes"), gestor, CLIENTE_VALIDO)).andExpect(status().isCreated());
        mvc.perform(json(post("/api/clientes"), inspector, CLIENTE_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Su rol no tiene permiso para esta operacion."));
        mvc.perform(json(post("/api/clientes"), cliente, CLIENTE_VALIDO)).andExpect(status().isForbidden());
    }

    @Test
    void unEoriMalFormadoSeRechazaAntesDeLlegarALaBase() throws Exception {
        mvc.perform(json(post("/api/clientes"), gestor, CLIENTE_VALIDO.replace("DE123456789012345", "de123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.numeroEori").exists());
    }

    @Test
    void losErroresDeNegocioLleganConSuCodigoYMensaje() throws Exception {
        when(clientes.crear(any())).thenThrow(
                ApiException.invalido("El pais del importador debe ser miembro de la Union Europea."));

        mvc.perform(json(post("/api/clientes"), gestor, CLIENTE_VALIDO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value("El pais del importador debe ser miembro de la Union Europea."))
                .andExpect(jsonPath("$.ruta").value("/api/clientes"));
    }

    // ---------- Usuarios y auditoria: solo el administrador ----------

    @Test
    void soloElAdministradorGestionaUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", admin)).andExpect(status().isOk());
        for (String otro : List.of(gestor, inspector, cliente)) {
            mvc.perform(get("/api/usuarios").header("Authorization", otro)).andExpect(status().isForbidden());
        }
    }

    @Test
    void soloElAdministradorConsultaLaAuditoria() throws Exception {
        mvc.perform(get("/api/auditoria").header("Authorization", gestor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria").header("Authorization", cliente)).andExpect(status().isForbidden());
    }

    // ---------- Inspecciones ----------

    @Test
    void soloQuienTieneInspeccionRegistrarInspecciona() throws Exception {
        String cuerpo = "{\"resultado\":\"APROBADO\",\"humedadPorcentaje\":11.2}";
        when(inspecciones.registrar(anyInt(), any())).thenReturn(new InspeccionServicio.InspeccionRegistrada(null, "APROBADO"));

        mvc.perform(json(post("/api/lotes/1/inspecciones"), inspector, cuerpo)).andExpect(status().isCreated());
        mvc.perform(json(post("/api/lotes/1/inspecciones"), gestor, cuerpo)).andExpect(status().isForbidden());
    }

    @Test
    void unResultadoDeInspeccionFueraDelDominioEs400() throws Exception {
        mvc.perform(json(post("/api/lotes/1/inspecciones"), inspector, "{\"resultado\":\"MAS_O_MENOS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.resultado").exists());
    }

    // ---------- Ordenes: el cliente consulta, no crea ----------

    @Test
    void elClienteConsultaSusOrdenesPeroNoCrea() throws Exception {
        mvc.perform(get("/api/ordenes").header("Authorization", cliente)).andExpect(status().isOk());
        mvc.perform(json(post("/api/ordenes"), cliente, "{\"idCliente\":1,\"incoterm\":\"FOB\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ordenes").header("Authorization", inspector)).andExpect(status().isForbidden());
    }

    // ---------- Tokens ----------

    @Test
    void unUsuarioInactivadoPierdeElAccesoAunqueSuTokenSigaVigente() throws Exception {
        String inactivo = token(9, Rol.GESTOR, null, "INACTIVO", PERMISOS_GESTOR);

        mvc.perform(get("/api/ordenes").header("Authorization", inactivo)).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenFalsoEs401() throws Exception {
        mvc.perform(get("/api/ordenes").header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.falso.falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void losPermisosSeLeenEnCadaPeticion() throws Exception {
        // Mismo token del gestor, pero el administrador le quito ORDENES_GESTIONAR en la base.
        Set<String> menos = new HashSet<>(Set.of(PERMISOS_GESTOR));
        menos.remove("ORDENES_GESTIONAR");
        token(2, Rol.GESTOR, null, "ACTIVO", menos.toArray(String[]::new));

        mvc.perform(get("/api/ordenes").header("Authorization", gestor)).andExpect(status().isForbidden());
    }
}
