-- NOMBRE: NOT NULL y NULL a la vez
-- ESPERADO: error
-- MENSAJE: se declara NOT NULL y NULL a la vez
-- LINEA: 11

-- La gramatica acepta las restricciones en cualquier orden para poder dar un
-- mensaje que explique, en vez de un error de sintaxis.

CREATE TABLE cosas (
  id     INT PRIMARY KEY,
  nombre VARCHAR(10) NOT NULL NULL
);
