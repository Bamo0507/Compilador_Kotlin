-- NOMBRE: Tabla que no existe
-- ESPERADO: error
-- MENSAJE: la tabla 'fantasma' no existe
-- LINEA: 17

-- El FROM se resuelve contra el catalogo, que es lo que el DBMS sabe de la
-- base antes de ejecutar nada.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre FROM fantasma;
