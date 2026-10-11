// NOMBRE: TAC: subexpresiones comunes
// SALIDA: 24
// SALIDA: 28

// En x, a * b aparece dos veces y el GDA la calcula una sola vez. En y no se
// comparte: incrementar cambia a entre las dos apariciones, asi que valen 12 y 16.
let a: integer = 3;
let b: integer = 4;

function incrementar(): integer {
  a = a + 1;
  return 0;
}

let x: integer = (a * b) + (a * b);
let y: integer = a * b + incrementar() + a * b;

print(x);
print(y);
