package com.ritech.exports.usuarios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** Roles fijos del sistema; los permisos llegan por la tabla puente roles_permisos (R20). */
@Entity
@Table(name = "roles")
@Getter
@Setter
public class Rol {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String GESTOR = "GESTOR_EXPORTACIONES";
    public static final String INSPECTOR = "INSPECTOR_CALIDAD";
    public static final String CLIENTE_UE = "CLIENTE_UE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Integer idRol;

    @Column(name = "nombre_rol")
    private String nombreRol;

    private String descripcion;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "roles_permisos",
            joinColumns = @JoinColumn(name = "id_rol"),
            inverseJoinColumns = @JoinColumn(name = "id_permiso"))
    private Set<Permiso> permisos = new HashSet<>();
}
