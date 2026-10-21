-- NOMBRE: Llave foranea que no apunta a nada
-- ESPERADO: error
-- MENSAJE: no existe en
-- LINEA: 21

-- El valor tiene que estar en la tabla referida, y el DBMS lo busca por su
-- indice automatico.

CREATE TABLE deptos (
  id     INT PRIMARY KEY,
  nombre VARCHAR(40) NOT NULL
);

CREATE TABLE empleados (
  id    INT PRIMARY KEY,
  depto INT REFERENCES deptos(id)
);

INSERT INTO deptos VALUES (1, 'Ventas');

INSERT INTO empleados VALUES (1, 99);
