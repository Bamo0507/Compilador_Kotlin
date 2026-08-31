// NOMBRE: Aritmética con string
// ESPERADO: linea 5, "no se puede aplicar a 'integer' y 'string'"

// El lenguaje NO convierte a string implicitamente: sumar no es concatenar.
let malo: integer = 1 + "a";
