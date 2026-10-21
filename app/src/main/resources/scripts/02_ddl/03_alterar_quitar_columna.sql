-- NOMBRE: Alterar para quitar una columna
-- ESPERADO: sin errores
-- FILAS: 2

-- Al quitar la columna, el CSV se reescribe sin ella y el esquema deja de
-- declararla.

CREATE TABLE notas (
  id       INT PRIMARY KEY,
  materia  VARCHAR(40) NOT NULL,
  borrador TEXT
);

INSERT INTO notas VALUES (1, 'Compiladores', 'sin revisar'), (2, 'Redes', NULL);

ALTER TABLE notas DROP COLUMN borrador;

SELECT id, materia FROM notas ORDER BY id;

DROP TABLE notas;
