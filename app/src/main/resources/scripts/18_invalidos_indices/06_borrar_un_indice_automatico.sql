-- NOMBRE: Borrar el indice de la clave primaria
-- ESPERADO: error
-- MENSAJE: no se puede borrar
-- LINEA: 17

-- Ese indice respalda la clave primaria: borrarlo dejaria la restriccion sin
-- con que verificarse.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

DROP INDEX pk_empleados;
