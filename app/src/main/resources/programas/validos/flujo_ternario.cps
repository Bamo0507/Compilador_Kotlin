// NOMBRE: Flujo: ternario y booleanos como valor
// SALIDA: impar
// SALIDA: true
// SALIDA: 1.5

// El ternario y el && como valor se traducen con saltos: cada rama copia su valor
// en el mismo temporal.
let n: integer = 7;

let paridad: string = n % 2 == 0 ? "par" : "impar";
let enRango: boolean = n > 0 && n < 10;
let f: float = n > 5 ? 1.5 : 2.5;

print(paridad);
print(enRango);
print(f);
