// NOMBRE: Condición que no es booleana
// ESPERADO: linea 5, "La condición de 'if' debe ser boolean, no 'integer'"

let x: integer = 5;
if (x) {
  print(x);
}
