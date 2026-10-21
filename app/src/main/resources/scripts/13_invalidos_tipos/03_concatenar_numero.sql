-- NOMBRE: Concatenar un numero
-- ESPERADO: error
-- MENSAJE: no se puede aplicar ||
-- LINEA: 16

-- El || es lo contrario del +: solo acepta texto.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre || salario FROM empleados;
