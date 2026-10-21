-- NOMBRE: Referencia a una columna que no es unica
-- ESPERADO: error
-- MENSAJE: que no es unica
-- LINEA: 15

-- Una llave foranea tiene que apuntar a algo que identifique una sola fila.

CREATE TABLE deptos (
  id     INT PRIMARY KEY,
  nombre VARCHAR(40) NOT NULL
);

CREATE TABLE empleados (
  id    INT PRIMARY KEY,
  depto VARCHAR(40) REFERENCES deptos(nombre)
);
