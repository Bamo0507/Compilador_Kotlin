// NOMBRE: print de un objeto
// ESPERADO: linea 9, "print solo acepta integer, float, string o boolean"

// print solo sabe imprimir valores simples: un objeto o una lista no tienen una forma
// de texto definida en el lenguaje. Para mostrar un objeto se imprimen sus campos.
class Punto { let x: integer = 1; }

let punto: Punto = new Punto();
print(punto);
