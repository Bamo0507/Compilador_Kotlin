-- NOMBRE: Comparar tipos distintos contra una subconsulta
-- ESPERADO: error
-- MENSAJE: no se puede comparar
-- LINEA: 16

-- La subconsulta devuelve texto y afuera se compara contra un entero.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre FROM empleados WHERE depto IN (SELECT nombre FROM empleados);
