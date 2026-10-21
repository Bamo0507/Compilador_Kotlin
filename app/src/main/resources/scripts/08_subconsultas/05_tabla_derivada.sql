-- NOMBRE: Tabla derivada en el FROM
-- ESPERADO: sin errores
-- FILAS: 2

-- Una consulta entre parentesis en el FROM se usa como tabla. Sus columnas
-- calculadas necesitan AS, porque sin nombre no se pueden nombrar afuera.

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

SELECT t.depto, t.planilla
FROM (SELECT depto, SUM(salario) AS planilla
        FROM empleados
       GROUP BY depto) AS t
WHERE t.planilla > 40000.00
ORDER BY t.depto;
DROP TABLE empleados;
DROP TABLE deptos;
