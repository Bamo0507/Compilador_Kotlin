// NOMBRE: Flujo: errores en ejecución atrapados
// SALIDA: división atrapada
// SALIDA: índice atrapado
// SALIDA: null atrapado

// Los tres chequeos del TAC: division entre cero, indice fuera de rango y acceso a
// null. Cada uno dispara un throw que el catch atrapa.
class Caja {
  let valor: integer;
}

let cero: integer = 0;
let lista: integer[] = [1, 2, 3];
let i: integer = 5;
let caja: Caja = null;

try {
  print(10 / cero);
} catch (e) {
  print("división atrapada");
}

try {
  print(lista[i]);
} catch (e) {
  print("índice atrapado");
}

try {
  print(caja.valor);
} catch (e) {
  print("null atrapado");
}
