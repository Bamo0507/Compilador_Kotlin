// NOMBRE: Función redeclarada
// ESPERADO: linea 7, "'duplicada' ya fue declarado en este ámbito"

function duplicada(): integer {
  return 1;
}
function duplicada(): integer {
  return 2;
}
