-- NOMBRE: WHERE que no da BOOLEAN
-- ESPERADO: error
-- MENSAJE: WHERE espera BOOLEAN
-- LINEA: 17

-- El WHERE decide si la fila sale, asi que su expresion tiene que ser una
-- condicion y no un numero.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre FROM empleados WHERE salario;
