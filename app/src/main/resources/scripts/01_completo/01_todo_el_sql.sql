-- NOMBRE: Todo el SQL de extremo a extremo
-- ESPERADO: sin errores
-- FILAS: 2

-- Un recorrido por todo lo que el DBMS entiende, en un solo script: las dos
-- tablas con sus restricciones, los indices, las cuatro sentencias de datos, y
-- al final las consultas que combinan join, agregacion y subconsulta.

CREATE TABLE deptos (
  id     INT PRIMARY KEY,
  nombre VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE empleados (
  id      INT PRIMARY KEY AUTOINCREMENT,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT REFERENCES deptos(id),
  salario DECIMAL(10,2) DEFAULT 12000.00,
  activo  BOOLEAN,
  ingreso DATE
);

CREATE INDEX idx_empleados_depto ON empleados(depto);

INSERT INTO deptos VALUES (1, 'Ventas'), (2, 'Soporte'), (3, 'Datos');

INSERT INTO empleados (nombre, depto, salario, activo, ingreso) VALUES
  ('Ana',   1, 25000.00, TRUE,  DATE '2021-03-01'),
  ('Bruno', 1, 18000.00, TRUE,  DATE '2022-07-15'),
  ('Cora',  2, 21000.50, FALSE, DATE '2020-01-20'),
  ('Dan',   2, 19500.00, TRUE,  DATE '2023-11-02'),
  ('Elsa',  3, 31000.00, TRUE,  DATE '2019-05-30');

-- El que entra sin salario toma el valor por omision, y su id lo pone el
-- AUTOINCREMENT.
INSERT INTO empleados (nombre, depto, activo) VALUES ('Fabio', 3, TRUE);

UPDATE empleados SET salario = salario * 1.10 WHERE depto = 1;

DELETE FROM empleados WHERE activo = FALSE;

-- La tabla completa, ya con los cambios aplicados.
SELECT id, nombre, depto, salario, activo, ingreso
FROM empleados
ORDER BY id;

-- Un join con alias y una columna calculada.
SELECT e.nombre, d.nombre AS departamento, e.salario * 12 AS anual
FROM empleados e
JOIN deptos d ON d.id = e.depto
ORDER BY e.nombre;

-- Agregacion por grupo, filtrada por HAVING.
SELECT d.nombre AS departamento, COUNT(*) AS cuantos, SUM(e.salario) AS planilla
FROM empleados e
JOIN deptos d ON d.id = e.depto
GROUP BY d.nombre
HAVING COUNT(*) >= 2
ORDER BY d.nombre;

-- Una subconsulta correlacionada: quien gana mas que el promedio de su depto.
SELECT nombre, salario
FROM empleados e
WHERE salario > (SELECT AVG(salario) FROM empleados WHERE depto = e.depto)
ORDER BY nombre;

-- El indice sirve a una busqueda por igualdad sobre la columna indexada.
SELECT nombre FROM empleados WHERE depto = 3 ORDER BY nombre;

DROP INDEX idx_empleados_depto;
DROP TABLE empleados;
DROP TABLE deptos;
