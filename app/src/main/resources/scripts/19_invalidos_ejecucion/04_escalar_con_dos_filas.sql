-- NOMBRE: Subconsulta escalar que devuelve dos filas
-- ESPERADO: error
-- MENSAJE: se esperaba a lo sumo una
-- LINEA: 19

-- La forma la revisa el analizador; cuantas filas salen solo se sabe
-- ejecutando.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

INSERT INTO empleados (id, nombre) VALUES (1, 'Ana'), (2, 'Beto');

SELECT nombre FROM empleados WHERE id = (SELECT id FROM empleados);
