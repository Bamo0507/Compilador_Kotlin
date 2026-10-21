-- NOMBRE: Agregacion en el WHERE
-- ESPERADO: error
-- MENSAJE: WHERE no puede usar funciones de agregacion
-- LINEA: 17

-- El WHERE corre antes de agrupar, cuando todavia no hay grupos que contar.
-- Eso es lo que hace el HAVING.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT depto FROM empleados WHERE COUNT(*) > 1 GROUP BY depto;
