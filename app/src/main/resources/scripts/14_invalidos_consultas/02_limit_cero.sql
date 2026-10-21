-- NOMBRE: LIMIT con cero
-- ESPERADO: error
-- MENSAJE: LIMIT espera un entero mayor que cero
-- LINEA: 16

-- Pedir cero filas no es un filtro, es un script escrito por error.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre FROM empleados ORDER BY id LIMIT 0;
