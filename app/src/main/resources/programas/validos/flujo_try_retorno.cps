// NOMBRE: Flujo: retorno desde try y catch
// SALIDA: 5
// SALIDA: -1

// Las dos ramas retornan, asi que la funcion garantiza un valor en todos los caminos.
function dividir(a: integer, b: integer): integer {
  try {
    return a / b;
  } catch (error) {
    return -1;
  }
}

print(dividir(10, 2));
print(dividir(10, 0));
