-- NOMBRE: Calificador que nadie puso en el FROM
-- ESPERADO: error
-- MENSAJE: no hay ninguna tabla llamada 'x'
-- LINEA: 17

-- La 'x' no es alias de nada. Sin esta regla la columna simplemente no
-- saldria y nadie diria por que.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT x.nombre FROM empleados e;
