// NOMBRE: Ámbito: global declarada después de la función
// SALIDA: 5

// El cuerpo de la funcion se revisa al final, cuando todas las globales ya existen.
// Llamarla antes de declarar la global fallaria en ejecucion, igual que en TypeScript.
function mostrar() {
  print(contador);
}

let contador: integer = 5;
mostrar();
