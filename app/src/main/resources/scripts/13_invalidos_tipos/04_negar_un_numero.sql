-- NOMBRE: NOT sobre un numero
-- ESPERADO: error
-- MENSAJE: NOT espera BOOLEAN
-- LINEA: 17

-- NOT invierte un valor logico. Un entero no es un valor logico, ni aunque
-- valga cero o uno.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre FROM empleados WHERE NOT salario;
