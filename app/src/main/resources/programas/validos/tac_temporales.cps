// NOMBRE: TAC: reciclaje de temporales
// SALIDA: 14

// La expresion de la diapositiva 19. Sin GDA ni reciclaje usa cinco temporales; con
// b - c calculado una sola vez y el pool con conteo de usos, le bastan dos.
let a: integer = 2;
let b: integer = 5;
let c: integer = 3;
let d: integer = 4;

let r: integer = a + a * (b - c) + (b - c) * d;
print(r);
