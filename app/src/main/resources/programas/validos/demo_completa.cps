// NOMBRE: Demostración completa
// SALIDA: Toby ladra.
// SALIDA: 90
// SALIDA: 85
// SALIDA: 100
// SALIDA: 120
// SALIDA: 13

// Programa de demostración de Compiscript

class Animal {
  let nombre: string;

  function constructor(nombre: string) {
    this.nombre = nombre;
  }

  function hablar(): string {
    return this.nombre + " hace ruido.";
  }
}

class Perro : Animal {
  function hablar(): string {
    return this.nombre + " ladra.";
  }
}

function factorial(n: integer): integer {
  if (n <= 1) { return 1; }
  return n * factorial(n - 1);
}

let perro: Perro = new Perro("Toby");
print(perro.hablar());

let notas: integer[] = [90, 85, 100];
foreach (nota in notas) {
  if (nota < 60) { continue; }
  print(nota);
}

print(factorial(5));
print(3 + 5 * 2);
