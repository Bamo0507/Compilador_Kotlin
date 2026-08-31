// NOMBRE: Ámbitos anidados
// SALIDA: 1
// SALIDA: 2

// Un bloque abre su propio entorno, pero asignar dentro de el modifica la
// variable de afuera: es assign, no define.
let x: integer = 1;
print(x);

{
  x = 2;
}

print(x);
