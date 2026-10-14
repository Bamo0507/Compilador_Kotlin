// NOMBRE: Clase declarada dentro de un bloque
// ESPERADO: linea 6, "solo se puede declarar en el nivel superior"

// Las clases son tipos del programa completo: no se declaran dentro de un bloque.
if (true) {
  class Punto {
    let x: integer = 0;
  }
}
