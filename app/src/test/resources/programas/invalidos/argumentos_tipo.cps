// ESPERADO: linea 7, "El argumento 1 debe ser 'string', no 'integer'"

function saludar(nombre: string): string {
  return "Hola " + nombre;
}

print(saludar(42));
