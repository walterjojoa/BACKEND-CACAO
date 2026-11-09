package com.ritech.exports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ritech.exports.config.DatosIniciales;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** La aplicacion completa arranca (seguridad, aspecto, controladores) y protege la API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArranqueTest {

    /** Sin MySQL no hay tabla usuarios; la carga de contrasenas semilla se omite en esta prueba. */
    @MockitoBean
    DatosIniciales datosIniciales;

    @Autowired
    MockMvc mvc;

    @Test
    void sinTokenLaApiResponde401EnEspanol() throws Exception {
        mvc.perform(get("/api/lotes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Debe iniciar sesion o el token vencio."));
    }

    @Test
    void laDocumentacionEsPublica() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void elLoginValidaElCuerpo() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"no-es-correo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.email").exists())
                .andExpect(jsonPath("$.campos.password").exists());
    }
}
