-- NOMBRE: Comparar con NULL
-- ESPERADO: advertencia
-- MENSAJE: nunca es verdadero
-- LINEA: 19

-- `= NULL` da NULL, no verdadero, asi que la condicion nunca se cumple. Es
-- valido, asi que el script corre igual: solo se avisa.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

INSERT INTO empleados (id, nombre) VALUES (1, 'Ana');

SELECT nombre FROM empleados WHERE salario = NULL;

DROP TABLE empleados;
