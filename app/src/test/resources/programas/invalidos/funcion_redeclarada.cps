// ESPERADO: linea 6, "'duplicada' ya fue declarado en este ámbito"

function duplicada(): integer {
  return 1;
}
function duplicada(): integer {
  return 2;
}
