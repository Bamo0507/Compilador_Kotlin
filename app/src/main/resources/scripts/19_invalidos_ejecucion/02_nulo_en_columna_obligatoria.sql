-- NOMBRE: Poner nulo en una columna que no lo acepta
-- ESPERADO: error
-- MENSAJE: la columna 'nombre' no acepta nulos
-- LINEA: 18

-- El UPDATE revisa la fila entera como queda, no solo la celda que toco.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

INSERT INTO empleados (id, nombre) VALUES (1, 'Ana');

UPDATE empleados SET nombre = NULL WHERE id = 1;
