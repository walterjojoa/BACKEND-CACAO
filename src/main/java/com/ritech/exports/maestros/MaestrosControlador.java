package com.ritech.exports.maestros;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Maestros", description = "SCRUM-13 productos con codigo HS; SCRUM-14 productores y fincas")
public class MaestrosControlador {

    private final MaestrosServicio servicio;

    public MaestrosControlador(MaestrosServicio servicio) {
        this.servicio = servicio;
    }

    // ----- Productos: cualquier usuario autenticado los lee (los necesita para registrar lotes)

    @GetMapping("/productos")
    public List<Producto> productos() {
        return servicio.productos();
    }

    @GetMapping("/productos/{id}")
    public Producto producto(@PathVariable Integer id) {
        return servicio.producto(id);
    }

    @PostMapping("/productos")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    @ResponseStatus(HttpStatus.CREATED)
    public Producto crearProducto(@Valid @RequestBody MaestrosServicio.ProductoSolicitud s) {
        return servicio.guardarProducto(null, s);
    }

    @PutMapping("/productos/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    public Producto editarProducto(@PathVariable Integer id, @Valid @RequestBody MaestrosServicio.ProductoSolicitud s) {
        return servicio.guardarProducto(id, s);
    }

    @DeleteMapping("/productos/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_GESTIONAR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarProducto(@PathVariable Integer id) {
        servicio.eliminarProducto(id);
    }

    // ----- Productores y fincas

    @GetMapping("/productores")
    @PreAuthorize("hasAnyAuthority('PRODUCTORES_GESTIONAR','LOTES_TRAZABILIDAD')")
    public List<Productor> productores() {
        return servicio.productores();
    }

    @GetMapping("/productores/{id}")
    @PreAuthorize("hasAnyAuthority('PRODUCTORES_GESTIONAR','LOTES_TRAZABILIDAD')")
    public Productor productor(@PathVariable Integer id) {
        return servicio.productor(id);
    }

    @PostMapping("/productores")
    @PreAuthorize("hasAuthority('PRODUCTORES_GESTIONAR')")
    @ResponseStatus(HttpStatus.CREATED)
    public Productor crearProductor(@Valid @RequestBody MaestrosServicio.ProductorSolicitud s) {
        return servicio.guardarProductor(null, s);
    }

    @PutMapping("/productores/{id}")
    @PreAuthorize("hasAuthority('PRODUCTORES_GESTIONAR')")
    public Productor editarProductor(@PathVariable Integer id, @Valid @RequestBody MaestrosServicio.ProductorSolicitud s) {
        return servicio.guardarProductor(id, s);
    }

    @GetMapping("/productores/{id}/fincas")
    @PreAuthorize("hasAnyAuthority('PRODUCTORES_GESTIONAR','LOTES_TRAZABILIDAD')")
    public List<Finca> fincas(@PathVariable Integer id) {
        return servicio.fincasDe(id);
    }

    @PostMapping("/productores/{id}/fincas")
    @PreAuthorize("hasAuthority('PRODUCTORES_GESTIONAR')")
    @ResponseStatus(HttpStatus.CREATED)
    public Finca crearFinca(@PathVariable Integer id, @Valid @RequestBody MaestrosServicio.FincaSolicitud s) {
        return servicio.crearFinca(id, s);
    }

    @GetMapping("/fincas/{id}")
    @PreAuthorize("hasAnyAuthority('PRODUCTORES_GESTIONAR','LOTES_TRAZABILIDAD')")
    public Finca finca(@PathVariable Integer id) {
        return servicio.finca(id);
    }

    @PutMapping("/fincas/{id}")
    @PreAuthorize("hasAuthority('PRODUCTORES_GESTIONAR')")
    public Finca editarFinca(@PathVariable Integer id, @Valid @RequestBody MaestrosServicio.FincaSolicitud s) {
        return servicio.editarFinca(id, s);
    }
}
