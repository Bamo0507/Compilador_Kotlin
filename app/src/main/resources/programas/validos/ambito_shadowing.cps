// NOMBRE: Shadowing de variables
// SALIDA: 2
// SALIDA: 1

// Declarar con `let` dentro del bloque crea OTRA variable que tapa la de
// afuera y muere con el bloque.
let x: integer = 1;

{
  let x: integer = 2;
  print(x);
}

print(x);
