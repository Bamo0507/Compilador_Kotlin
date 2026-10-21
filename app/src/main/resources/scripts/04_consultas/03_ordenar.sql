-- NOMBRE: Ordenar ascendente y descendente
-- ESPERADO: sin errores
-- FILAS: 6

-- Los nulos van al final en ASC y al principio en DESC, que es como se ordena
-- cuando falta el dato.

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

SELECT nombre, salario FROM empleados ORDER BY salario DESC;

SELECT nombre, salario FROM empleados ORDER BY salario ASC;

-- Dos criterios: primero el depto, y dentro de cada depto el nombre.
SELECT depto, nombre FROM empleados ORDER BY depto ASC, nombre DESC;
DROP TABLE empleados;
DROP TABLE deptos;
