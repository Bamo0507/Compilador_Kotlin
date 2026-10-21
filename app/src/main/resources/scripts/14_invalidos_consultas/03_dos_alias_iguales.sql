-- NOMBRE: Dos columnas con el mismo alias
-- ESPERADO: error
-- MENSAJE: hay dos columnas llamadas 'x'
-- LINEA: 16

-- Dos columnas con el mismo nombre no se pueden distinguir desde afuera.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT id AS x, nombre AS x FROM empleados;
