-- NOMBRE: Alias del SELECT usado en el WHERE
-- ESPERADO: error
-- MENSAJE: no esta disponible en WHERE
-- LINEA: 17

-- El WHERE se evalua antes que el SELECT, asi que el alias todavia no existe.
-- En el ORDER BY, que corre despues, si se puede.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT salario * 12 AS anual FROM empleados WHERE anual > 100000.00;
