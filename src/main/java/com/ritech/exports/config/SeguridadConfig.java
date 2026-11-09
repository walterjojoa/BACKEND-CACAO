package com.ritech.exports.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritech.exports.comun.RespuestaError;
import com.ritech.exports.seguridad.JwtFiltro;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

    @Bean
    SecurityFilterChain cadena(HttpSecurity http, JwtFiltro jwtFiltro, ObjectMapper json) throws Exception {
        http
                .csrf(c -> c.disable())
                .cors(c -> {
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/login", "/salud", "/docs", "/docs/**",
                                "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> escribir(res, json, HttpStatus.UNAUTHORIZED,
                                "Debe iniciar sesion o el token vencio.", req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) -> escribir(res, json, HttpStatus.FORBIDDEN,
                                "Su rol no tiene permiso para esta operacion.", req.getRequestURI())))
                .addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void escribir(jakarta.servlet.http.HttpServletResponse res, ObjectMapper json,
                                 HttpStatus estado, String mensaje, String ruta) throws java.io.IOException {
        res.setStatus(estado.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getOutputStream(), new RespuestaError(estado.value(), estado.getReasonPhrase(),
                mensaje, ruta, null, OffsetDateTime.now()));
    }

    /** El filtro ya corre dentro de la cadena de seguridad; se evita que Boot lo registre otra vez. */
    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<JwtFiltro> noRegistrarDosVeces(JwtFiltro f) {
        var registro = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(f);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    PasswordEncoder codificador() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource cors(@Value("${ritech.cors.origenes}") String origenes) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        c.setExposedHeaders(List.of("Content-Disposition"));
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", c);
        return fuente;
    }
}
