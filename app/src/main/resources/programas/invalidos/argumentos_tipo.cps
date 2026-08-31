// NOMBRE: Tipo de argumento incorrecto
// ESPERADO: linea 8, "El argumento 1 debe ser 'string', no 'integer'"

function saludar(nombre: string): string {
  return "Hola " + nombre;
}

print(saludar(42));
