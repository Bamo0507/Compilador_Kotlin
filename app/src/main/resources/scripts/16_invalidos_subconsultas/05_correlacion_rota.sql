-- NOMBRE: Correlacion a una columna que no existe
-- ESPERADO: error
-- MENSAJE: no tiene columna 'sueldo'
-- LINEA: 17

-- La de adentro nombra 'e.sueldo', y la tabla de afuera no tiene esa columna.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre FROM empleados e
WHERE EXISTS (SELECT 1 FROM empleados WHERE salario > e.sueldo);
