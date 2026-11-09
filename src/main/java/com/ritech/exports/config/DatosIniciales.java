package com.ritech.exports.config;

import com.ritech.exports.usuarios.Usuario;
import com.ritech.exports.usuarios.UsuarioRepositorio;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * El script deja los usuarios semilla con password_hash marcador ("REEMPLAZAR_POR_HASH_REAL"),
 * con el que nadie puede entrar. Si se define SEED_PASSWORD, al arrancar se les pone ese
 * hash bcrypt real. Solo toca usuarios que todavia tienen el marcador.
 */
@Component
public class DatosIniciales implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;
    private final String passwordInicial;

    public DatosIniciales(UsuarioRepositorio usuarios, PasswordEncoder codificador,
                          @Value("${ritech.semilla.password-inicial:}") String passwordInicial) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.passwordInicial = passwordInicial;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Usuario> pendientes = usuarios.conHashMarcador();
        if (pendientes.isEmpty()) {
            return;
        }
        if (passwordInicial == null || passwordInicial.isBlank()) {
            log.warn("{} usuario(s) semilla sin contrasena real. Defina SEED_PASSWORD para activarlos.",
                    pendientes.size());
            return;
        }
        String hash = codificador.encode(passwordInicial);
        pendientes.forEach(u -> u.setPasswordHash(hash));
        log.info("Contrasena inicial asignada a {} usuario(s) semilla: {}", pendientes.size(),
                pendientes.stream().map(Usuario::getEmail).toList());
    }
}
