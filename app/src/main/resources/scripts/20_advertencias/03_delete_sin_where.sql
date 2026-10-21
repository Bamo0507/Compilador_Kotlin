-- NOMBRE: DELETE sin WHERE
-- ESPERADO: advertencia
-- MENSAJE: esto afecta todas las filas
-- LINEA: 19

-- Vaciar la tabla entera es valido y a veces es lo que se quiere, asi que el
-- DBMS avisa y sigue.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

INSERT INTO empleados (id, nombre) VALUES (1, 'Ana'), (2, 'Beto');

DELETE FROM empleados;

SELECT COUNT(*) AS quedan FROM empleados;

DROP TABLE empleados;
