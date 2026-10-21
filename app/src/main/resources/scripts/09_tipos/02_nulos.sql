-- NOMBRE: Nulos y logica de tres valores
-- ESPERADO: sin errores
-- FILAS: 1

-- Un nulo no es cero ni cadena vacia: es la ausencia del dato. Cualquier
-- operacion con el da nulo, y por eso la fila no pasa el filtro.

CREATE TABLE medidas (
  id     INT PRIMARY KEY,
  valor  INT,
  nota   VARCHAR(20)
);

INSERT INTO medidas VALUES (1, 10, 'diez'), (2, NULL, NULL), (3, 30, '');

-- El nulo no sale ni con = ni con <>: hay que preguntarlo con IS NULL.
SELECT id FROM medidas WHERE valor = 10;
SELECT id FROM medidas WHERE valor <> 10 ORDER BY id;
SELECT id FROM medidas WHERE valor IS NULL;

-- La cadena vacia SI es un valor, y se distingue del nulo.
SELECT id, nota FROM medidas WHERE nota IS NOT NULL ORDER BY id;

-- El AND es la excepcion: FALSE AND NULL es FALSE, porque el resultado ya esta
-- decidido sin mirar el segundo operando.
SELECT id FROM medidas WHERE valor = 10 AND valor IS NOT NULL;

DROP TABLE medidas;
