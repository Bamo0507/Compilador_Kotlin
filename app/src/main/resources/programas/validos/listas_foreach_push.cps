// NOMBRE: Listas: push dentro de un foreach
// SALIDA: 3
// SALIDA: 20
// SALIDA: 40
// SALIDA: 60

// Se recorre una lista y se llena otra que empieza vacia.
let notas: integer[] = [10, 20, 30];
let dobles: integer[] = [];
foreach (nota in notas) {
  dobles.push(nota * 2);
}

print(dobles.length);
foreach (doble in dobles) {
  print(doble);
}
