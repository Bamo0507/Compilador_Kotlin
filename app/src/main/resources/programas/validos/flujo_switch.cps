// NOMBRE: Flujo: switch
// SALIDA: dos
// SALIDA: otro
// SALIDA: hola

// Un switch se traduce como una cadena de comparaciones y no como una tabla de
// saltos: un case puede ser un string. No hay fall-through: cada case termina
// saltando al final.
function nombre(n: integer): string {
  let resultado: string = "";
  switch (n) {
    case 1:
      resultado = "uno";
    case 2:
      resultado = "dos";
    default:
      resultado = "otro";
  }
  return resultado;
}

print(nombre(2));
print(nombre(7));

let saludo: string = "hola";
switch (saludo) {
  case "adios":
    print("adios");
  case "hola":
    print("hola");
}
