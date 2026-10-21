-- NOMBRE: Sumar texto
-- ESPERADO: error
-- MENSAJE: no se puede aplicar +
-- LINEA: 16

-- El + es aritmetico. Para pegar dos textos esta el ||.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre + 1 FROM empleados;
