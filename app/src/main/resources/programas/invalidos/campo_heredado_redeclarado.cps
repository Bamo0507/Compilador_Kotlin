// NOMBRE: Campo heredado declarado otra vez
// ESPERADO: linea 12, "ya se hereda de 'Animal'"

// Un objeto tiene una sola casilla por nombre: la subclase no puede declarar otra vez
// un campo de su superclase.
class Animal {
  let nombre: string = "";
  function presentar(): string { return this.nombre; }
}

class Perro : Animal {
  let nombre: string = "Toby";
}
