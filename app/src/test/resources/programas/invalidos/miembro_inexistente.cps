// ESPERADO: linea 8, "no tiene un miembro llamado 'edad'"

class Animal {
  let nombre: string;
}

let animal: Animal = new Animal();
print(animal.edad);
