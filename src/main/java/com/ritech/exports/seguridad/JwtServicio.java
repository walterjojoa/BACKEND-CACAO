package com.ritech.exports.seguridad;

import com.ritech.exports.usuarios.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtServicio {

    private final SecretKey clave;
    private final long horasValidez;

    public JwtServicio(@Value("${ritech.jwt.secret}") String secreto,
                       @Value("${ritech.jwt.horas-validez}") long horasValidez) {
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalStateException(
                    "Falta JWT_SECRET (minimo 32 caracteres). En desarrollo use el perfil local.");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.horasValidez = horasValidez;
    }

    public String emitir(Usuario u) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(u.getIdUsuario()))
                .claim("email", u.getEmail())
                .claim("rol", u.getRol().getNombreRol())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(horasValidez, ChronoUnit.HOURS)))
                .signWith(clave)
                .compact();
    }

    public long segundosValidez() {
        return horasValidez * 3600;
    }

    /** Id del usuario si el token es valido y no ha vencido. */
    public Optional<Integer> validar(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
            return Optional.of(Integer.valueOf(c.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
