-- NOMBRE: Falta el punto y coma
-- ESPERADO: error
-- MENSAJE: missing ';'
-- LINEA: 18

-- Cada sentencia termina en punto y coma. Sin el, el analizador sigue leyendo
-- la siguiente como si fuera parte de la misma.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre FROM empleados
SELECT id FROM empleados;
