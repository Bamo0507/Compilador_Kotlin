-- NOMBRE: Guardar texto en una columna INT
-- ESPERADO: error
-- MENSAJE: no se puede guardar
-- LINEA: 17

-- El tipo del valor tiene que caber en el de la columna, y esto se revisa
-- antes de ejecutar nada.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

INSERT INTO empleados (id, nombre, depto) VALUES (1, 'Ana', 'uno');
