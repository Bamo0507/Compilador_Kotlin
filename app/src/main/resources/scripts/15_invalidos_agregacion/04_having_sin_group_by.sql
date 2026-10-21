-- NOMBRE: HAVING sin GROUP BY ni agregacion
-- ESPERADO: error
-- MENSAJE: HAVING solo tiene sentido con GROUP BY
-- LINEA: 17

-- Sin grupos no hay nada que filtrar despues de agrupar: esa condicion iba en
-- el WHERE.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre FROM empleados HAVING salario > 100.00;
