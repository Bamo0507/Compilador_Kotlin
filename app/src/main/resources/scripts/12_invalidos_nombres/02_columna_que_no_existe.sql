-- NOMBRE: Columna que no existe
-- ESPERADO: error
-- MENSAJE: no existe la columna 'apellido'
-- LINEA: 16

-- La columna se busca en las tablas del FROM, y en ninguna de ellas esta.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT apellido FROM empleados;
