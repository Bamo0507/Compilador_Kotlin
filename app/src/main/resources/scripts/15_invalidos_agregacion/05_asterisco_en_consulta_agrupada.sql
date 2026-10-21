-- NOMBRE: Asterisco en una consulta agrupada
-- ESPERADO: error
-- MENSAJE: no se puede usar en una consulta agrupada
-- LINEA: 17

-- El asterisco pide todas las columnas, y en una consulta agrupada la mayoria
-- no tiene un valor unico por grupo.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT * FROM empleados GROUP BY depto;
