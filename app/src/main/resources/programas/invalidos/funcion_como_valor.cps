// NOMBRE: Función usada como valor
// ESPERADO: linea 12, "es una función: solo se puede llamar"

// Una funcion solo se puede llamar. Si interna se guardara para llamarla despues de
// que externa termina, leeria cuenta de un registro de activacion que ya no existe.
let guardada = plantilla;
function plantilla(): integer { return 0; }

function externa() {
  let cuenta: integer = 41;
  function interna(): integer { return cuenta + 1; }
  guardada = interna;
}
