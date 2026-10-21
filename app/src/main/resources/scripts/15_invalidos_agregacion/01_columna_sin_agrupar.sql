-- NOMBRE: Columna del SELECT sin agrupar
-- ESPERADO: error
-- MENSAJE: no esta en el GROUP BY
-- LINEA: 17

-- Si un depto tiene cinco empleados hay un grupo y cinco nombres: la consulta
-- no dice cual de los cinco deberia salir.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT depto, nombre, COUNT(*) FROM empleados GROUP BY depto;
