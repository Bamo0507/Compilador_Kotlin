// ESPERADO: linea 8, "no se puede aplicar a"

// Sentido semantico: una funcion no es un numero. Sin el `()` esto es la
// funcion misma, no su resultado.
function f(): integer {
  return 1;
}
let malo: integer = f * 2;
