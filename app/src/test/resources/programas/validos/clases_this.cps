// SALIDA: Firulais
// SALIDA: 7

class Mascota {
  let nombre: string;
  let edad: integer;

  function constructor(nombre: string) {
    this.nombre = nombre;
    this.edad = 0;
  }

  function cumplir(anios: integer): integer {
    this.edad = this.edad + anios;
    return this.edad;
  }

  function comoSeLlama(): string {
    return this.nombre;
  }
}

let mascota: Mascota = new Mascota("Firulais");
print(mascota.comoSeLlama());
print(mascota.cumplir(7));
