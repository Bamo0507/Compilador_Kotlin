// NOMBRE: Funciones: closures
// SALIDA: 42

// La funcion anidada ve la local de la de afuera porque su closure es el
// entorno donde fue DEFINIDA, no donde se la llama.
function crearContador(): integer {
  let cuenta: integer = 41;

  function siguiente(): integer {
    return cuenta + 1;
  }

  return siguiente();
}

print(crearContador());
