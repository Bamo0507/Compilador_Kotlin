-- NOMBRE: Falta una columna que no acepta nulos
-- ESPERADO: error
-- MENSAJE: no acepta nulos y no tiene valor por omision
-- LINEA: 17

-- La columna quedaria nula y no lo permite, y tampoco tiene DEFAULT que la
-- rellene.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

INSERT INTO empleados (id, depto) VALUES (1, 2);
