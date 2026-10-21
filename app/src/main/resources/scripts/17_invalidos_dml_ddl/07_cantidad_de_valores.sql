-- NOMBRE: Cantidad de valores que no cuadra
-- ESPERADO: error
-- MENSAJE: se esperaban 5 valores, se recibieron 3
-- LINEA: 16

-- Sin nombrar columnas hay que dar todas, en el orden del esquema.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

INSERT INTO empleados VALUES (1, 'Ana', 2);
