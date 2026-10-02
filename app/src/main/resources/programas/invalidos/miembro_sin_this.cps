// NOMBRE: Miembro de clase sin this
// ESPERADO: linea 9, "¿Quisiste decir 'this.cuenta'?"

// Como en TypeScript, un campo solo se alcanza con this: el nombre suelto busca una
// variable fuera de la clase.
class Contador {
  let cuenta: integer = 0;
  function incrementar() {
    cuenta = cuenta + 1;
  }
}
