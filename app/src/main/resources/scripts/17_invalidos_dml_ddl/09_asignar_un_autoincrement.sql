-- NOMBRE: Asignar una columna AUTOINCREMENT
-- ESPERADO: error
-- MENSAJE: es AUTOINCREMENT y no se puede asignar
-- LINEA: 16

-- El numero lo lleva el DBMS. Dejar que un UPDATE lo cambie romperia la
-- cuenta del siguiente.

CREATE TABLE cosas (
  id     INT PRIMARY KEY AUTOINCREMENT,
  nombre VARCHAR(20) NOT NULL
);

INSERT INTO cosas (nombre) VALUES ('uno');

UPDATE cosas SET id = 99 WHERE nombre = 'uno';
