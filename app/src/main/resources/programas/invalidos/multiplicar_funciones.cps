// NOMBRE: Multiplicar dos funciones
// ESPERADO: linea 9, "es una función: solo se puede llamar"

// Sentido semantico: una funcion no es un numero. Sin el `()` esto es la
// funcion misma, no su resultado, y una funcion solo se puede llamar.
function f(): integer {
  return 1;
}
let malo: integer = f * 2;
