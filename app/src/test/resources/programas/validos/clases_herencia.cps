// SALIDA: Toby ladra.
// SALIDA: Rex hace ruido.
// SALIDA: Toby ladra.

class Animal {
  let nombre: string;

  function constructor(nombre: string) {
    this.nombre = nombre;
  }

  function hablar(): string {
    return this.nombre + " hace ruido.";
  }
}

// Perro no declara constructor: lo hereda de Animal.
class Perro : Animal {
  function hablar(): string {
    return this.nombre + " ladra.";
  }
}

let perro: Perro = new Perro("Toby");
print(perro.hablar());

let otro: Animal = new Animal("Rex");
print(otro.hablar());

// La variable esta declarada Animal, pero el objeto ES un Perro: el despacho
// usa la clase real, no el tipo declarado.
let comoAnimal: Animal = new Perro("Toby");
print(comoAnimal.hablar());
