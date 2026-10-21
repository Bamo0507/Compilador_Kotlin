-- NOMBRE: Agregar una columna NOT NULL con filas adentro
-- ESPERADO: error
-- MENSAJE: use DEFAULT
-- LINEA: 19

-- Es la unica regla del DDL que necesita mirar los datos: sin valor por
-- omision, las filas que ya estan se quedarian con un nulo prohibido.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

INSERT INTO empleados (id, nombre) VALUES (1, 'Ana');

ALTER TABLE empleados ADD correo VARCHAR(40) NOT NULL;
