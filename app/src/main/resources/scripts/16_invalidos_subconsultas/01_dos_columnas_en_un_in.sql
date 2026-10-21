-- NOMBRE: Subconsulta de dos columnas en un IN
-- ESPERADO: error
-- MENSAJE: debe devolver una sola columna
-- LINEA: 16

-- El IN compara un valor contra una columna. Con dos no hay con cual comparar.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre FROM empleados WHERE depto IN (SELECT id, nombre FROM empleados);
