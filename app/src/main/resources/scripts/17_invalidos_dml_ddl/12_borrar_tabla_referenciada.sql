-- NOMBRE: Borrar una tabla que otra referencia
-- ESPERADO: error
-- MENSAJE: es referenciada por
-- LINEA: 18

-- Borrarla dejaria la llave foranea de la otra apuntando a nada.

CREATE TABLE deptos (
  id     INT PRIMARY KEY,
  nombre VARCHAR(40) NOT NULL
);

CREATE TABLE empleados (
  id    INT PRIMARY KEY,
  depto INT REFERENCES deptos(id)
);

DROP TABLE deptos;
