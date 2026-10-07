package sgspe

import org.springframework.boot.runApplication
import wasichai.core.autoconfigure.WasichaiApplication

// el servidor entero: los starters de wasichai en el classpath hacen el resto.
// nunca bajo el paquete `wasichai`: el escaneo de componentes registraría dos veces los controladores de la librería
// (WAS-ADR-0024). ArchitectureTest lo vigila
@WasichaiApplication
class SgspeApplication

fun main(args: Array<String>) {
    runApplication<SgspeApplication>(*args)
}
