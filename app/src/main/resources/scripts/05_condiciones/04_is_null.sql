-- NOMBRE: Preguntar por nulos
-- ESPERADO: sin errores
-- FILAS: 5

-- IS NULL es la unica forma de preguntar por un nulo: `salario = NULL` da
-- nulo, no verdadero, y la fila nunca saldria.

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

SELECT nombre FROM empleados WHERE salario IS NULL;

SELECT nombre FROM empleados WHERE salario IS NOT NULL ORDER BY nombre;
DROP TABLE empleados;
DROP TABLE deptos;
