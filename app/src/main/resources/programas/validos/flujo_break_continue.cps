// NOMBRE: Flujo: break y continue
// SALIDA: 90

let notas: integer[] = [50, 90, 100, 70];

foreach (n in notas) {
  if (n < 60) { continue; }
  if (n == 100) { break; }
  print(n);
}
