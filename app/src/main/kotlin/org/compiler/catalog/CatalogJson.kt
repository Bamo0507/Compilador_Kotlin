package org.compiler.catalog

import kotlinx.serialization.json.Json

/**
 * La configuracion con la que se leen y escriben los .json de esquema.
 *
 * `classDiscriminator` es lo que hace que una Constraint salga como
 * `{"constraint": "NOT_NULL"}` en vez del `"type"` que kotlinx pone por omision.
 * Sin eso haria falta un serializador escrito a mano.
 */
internal val catalogJson = Json {
    classDiscriminator = "constraint"

    // El punto de elegir JSON sobre un binario es poder leerlo a ojo.
    prettyPrint = true

    encodeDefaults = true
}
