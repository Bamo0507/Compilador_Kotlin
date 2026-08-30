// SALIDA: mayor
// SALIDA: 5
// SALIDA: 0
// SALIDA: 0
// SALIDA: 1
// SALIDA: 2

let x: integer = 20;

if (x > 10) {
  print("mayor");
} else {
  print("menor o igual");
}

let i: integer = 0;
while (i < 5) {
  i = i + 1;
}
print(i);

do {
  i = i - 1;
} while (i > 0);
print(i);

for (let j: integer = 0; j < 3; j = j + 1) {
  print(j);
}
