// NOMBRE: Clases: constructor propio en la subclase
// SALIDA: Toby
// SALIDA: labrador

// La subclase declara su propio constructor con otra firma. El constructor no
// participa del subtipado, asi que no cuenta como sobrescritura.
class Animal {
  let nombre: string;
  function constructor(nombre: string) { this.nombre = nombre; }
}

class Perro : Animal {
  let raza: string;
  function constructor(nombre: string, raza: string) {
    this.nombre = nombre;
    this.raza = raza;
  }
}

let perro: Perro = new Perro("Toby", "labrador");
print(perro.nombre);
print(perro.raza);
