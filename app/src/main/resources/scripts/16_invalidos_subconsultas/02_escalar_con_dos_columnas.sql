-- NOMBRE: Subconsulta escalar con dos columnas
-- ESPERADO: error
-- MENSAJE: debe devolver una sola columna
-- LINEA: 18

-- Una subconsulta usada como valor tiene que devolver una sola columna. Que
-- devuelva una sola FILA no se puede saber sin leer los datos, y eso lo
-- verifica el motor.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT (SELECT id, nombre FROM empleados) FROM empleados;
