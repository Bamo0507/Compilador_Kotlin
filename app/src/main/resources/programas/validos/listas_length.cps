// NOMBRE: Listas: length
// SALIDA: 1
// SALIDA: 2
// SALIDA: 4
// SALIDA: 8
// SALIDA: 4

// length se lee en cada vuelta, asi que el ciclo ve los elementos que se agregan.
let potencias: integer[] = [1];
let i: integer = 0;
while (potencias.length < 4) {
  potencias.push(potencias[i] * 2);
  i = i + 1;
}

for (let j: integer = 0; j < potencias.length; j = j + 1) {
  print(potencias[j]);
}
print(potencias.length);
