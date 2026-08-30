// ESPERADO: linea 4, "no se puede aplicar a 'integer' y 'string'"

// El lenguaje NO convierte a string implicitamente: sumar no es concatenar.
let malo: integer = 1 + "a";
