-- NOMBRE: LIMIT sin ORDER BY
-- ESPERADO: advertencia
-- MENSAJE: LIMIT sin ORDER BY
-- LINEA: 19

-- Sin ORDER BY nadie garantiza cuales son las dos primeras filas: dependen
-- del orden en que esten guardadas.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

INSERT INTO empleados (id, nombre) VALUES (1, 'Ana'), (2, 'Beto'), (3, 'Cora');

SELECT nombre FROM empleados LIMIT 2;

DROP TABLE empleados;
