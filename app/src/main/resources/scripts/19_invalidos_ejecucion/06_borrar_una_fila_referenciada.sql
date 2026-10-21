-- NOMBRE: Borrar una fila que otra referencia
-- ESPERADO: error
-- MENSAJE: lo referencian
-- LINEA: 22

-- La referencia se revisa al reves: antes de borrar, el DBMS busca quien
-- apunta a esa fila.

CREATE TABLE deptos (
  id     INT PRIMARY KEY,
  nombre VARCHAR(40) NOT NULL
);

CREATE TABLE empleados (
  id    INT PRIMARY KEY,
  depto INT REFERENCES deptos(id)
);

INSERT INTO deptos VALUES (1, 'Ventas');
INSERT INTO empleados VALUES (1, 1), (2, 1);

DELETE FROM deptos WHERE id = 1;
