-- NOMBRE: Alterar para agregar una columna
-- ESPERADO: sin errores
-- FILAS: 3

-- Agregar una columna NOT NULL sobre una tabla con filas exige DEFAULT: sin el,
-- las filas que ya estan quedarian con un nulo prohibido.

CREATE TABLE libros (
  id     INT PRIMARY KEY,
  titulo VARCHAR(80) NOT NULL
);

INSERT INTO libros VALUES (1, 'Rayuela'), (2, 'Pedro Paramo'), (3, 'Ficciones');

ALTER TABLE libros ADD paginas INT;
ALTER TABLE libros ADD prestado BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE libros SET paginas = 600 WHERE id = 1;

SELECT id, titulo, paginas, prestado FROM libros ORDER BY id;

DROP TABLE libros;
