// NOMBRE: Clases: despacho de un método sobrescrito
// SALIDA: ...
// SALIDA: guau

// a esta declarada como Animal, pero la segunda vez guarda un Perro. La llamada es la
// misma en el TAC: busca hablar en la tabla de metodos del objeto, y la tabla de
// Perro tiene Perro.hablar en esa posicion.
class Animal {
  function hablar(): string { return "..."; }
}

class Perro : Animal {
  function hablar(): string { return "guau"; }
}

let a: Animal = new Animal();
print(a.hablar());

a = new Perro();
print(a.hablar());
