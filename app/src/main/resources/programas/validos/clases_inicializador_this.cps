// NOMBRE: Clases: inicializador de campo con this
// SALIDA: 10
// SALIDA: 20

// Los inicializadores corren sobre el objeto que se esta construyendo, en orden:
// el de doble ya puede leer base.
class Medida {
  let base: integer = 10;
  let doble: integer = this.base * 2;
}

let medida: Medida = new Medida();
print(medida.base);
print(medida.doble);
