// NOMBRE: Miembro inexistente de la clase
// ESPERADO: linea 9, "no tiene un miembro llamado 'edad'"

class Animal {
  let nombre: string;
}

let animal: Animal = new Animal();
print(animal.edad);
