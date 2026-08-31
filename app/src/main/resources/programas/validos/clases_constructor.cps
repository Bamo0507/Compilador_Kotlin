// NOMBRE: Clases: constructor
// SALIDA: Rex hace ruido.
// SALIDA: Rex

class Animal {
  let nombre: string;

  function constructor(nombre: string) {
    this.nombre = nombre;
  }

  function hablar(): string {
    return this.nombre + " hace ruido.";
  }
}

let animal: Animal = new Animal("Rex");
print(animal.hablar());
print(animal.nombre);
