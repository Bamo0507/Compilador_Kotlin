-- NOMBRE: Pertenecer a una lista de valores
-- ESPERADO: sin errores
-- FILAS: 2

-- IN es la forma corta de encadenar igualdades con OR. Con NOT IN, un nulo en
-- la columna hace que la fila no salga, porque la comparacion da nulo.

CREATE TABLE deptos (
  id     INT PRIMARY KEY,
  nombre VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE empleados (
  id      INT PRIMARY KEY AUTOINCREMENT,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT REFERENCES deptos(id),
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

INSERT INTO deptos VALUES (1, 'Ventas'), (2, 'Soporte'), (3, 'Datos');

INSERT INTO empleados (nombre, depto, salario, activo) VALUES
  ('Ana',   1, 25000.00, TRUE),
  ('Bruno', 1, 18000.00, TRUE),
  ('Cora',  2, 21000.50, FALSE),
  ('Dan',   2, 19500.00, TRUE),
  ('Elsa',  3, 31000.00, TRUE),
  ('Fabio', 3, NULL,     TRUE);

SELECT nombre, depto FROM empleados WHERE depto IN (1, 3) ORDER BY nombre;

SELECT nombre, depto FROM empleados WHERE depto NOT IN (1) ORDER BY nombre;

SELECT nombre FROM empleados WHERE nombre IN ('Ana', 'Cora', 'Zoe') ORDER BY nombre;
DROP TABLE empleados;
DROP TABLE deptos;
