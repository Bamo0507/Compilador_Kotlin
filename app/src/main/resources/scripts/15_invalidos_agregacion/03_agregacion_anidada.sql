-- NOMBRE: Agregacion dentro de otra
-- ESPERADO: error
-- MENSAJE: no se puede anidar
-- LINEA: 17

-- Una agregacion reduce muchas filas a una. Meter otra adentro no tiene a que
-- aplicarse.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT SUM(AVG(salario)) FROM empleados;
