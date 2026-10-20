// NOMBRE: Listas: push
// SALIDA: 0
// SALIDA: 3
// SALIDA: 30
// SALIDA: 2.0
// SALIDA: 5
// SALIDA: 50

let notas: integer[] = [];
print(notas.length);
notas.push(10);
notas.push(20);
notas.push(30);
print(notas.length);
print(notas[2]);

// Un entero que entra a una lista de float se convierte.
let precios: float[] = [1.5];
precios.push(2);
print(precios[1]);

// Al pasar de 4 elementos el contenido se muda a uno mas grande, pero las dos
// variables apuntan a la misma ficha: la copia ve los elementos nuevos.
let copia: integer[] = notas;
notas.push(40);
notas.push(50);
print(copia.length);
print(copia[4]);
