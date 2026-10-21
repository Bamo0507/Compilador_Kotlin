-- NOMBRE: Clave primaria repetida
-- ESPERADO: error
-- MENSAJE: ya existe una fila con id = 1
-- LINEA: 18

-- Esto no se puede saber mirando el script: hay que mirar los datos, asi que
-- se reporta al ejecutar y no al analizar.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

INSERT INTO empleados (id, nombre) VALUES (1, 'Ana');
INSERT INTO empleados (id, nombre) VALUES (1, 'Beto');
