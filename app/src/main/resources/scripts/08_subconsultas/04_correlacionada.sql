-- NOMBRE: Subconsulta correlacionada
-- ESPERADO: sin errores
-- FILAS: 2

-- La de adentro nombra una columna de la de afuera, asi que se vuelve a
-- calcular por cada fila y no se puede guardar en cache.

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

SELECT nombre, salario
FROM empleados e
WHERE salario > (SELECT AVG(salario) FROM empleados WHERE depto = e.depto)
ORDER BY nombre;
DROP TABLE empleados;
DROP TABLE deptos;
