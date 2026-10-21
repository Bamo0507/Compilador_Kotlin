-- NOMBRE: Los nueve tipos de dato
-- ESPERADO: sin errores
-- FILAS: 2

-- INT, FLOAT, DECIMAL, CHAR, VARCHAR, TEXT, DATE, TIME y BOOLEAN, uno por
-- columna. El CHAR se rellena con espacios hasta su largo fijo.

CREATE TABLE muestras (
  entero   INT PRIMARY KEY,
  flotante FLOAT,
  exacto   DECIMAL(8,2),
  fijo     CHAR(3),
  variable VARCHAR(20),
  libre    TEXT,
  fecha    DATE,
  hora     TIME,
  bandera  BOOLEAN
);

INSERT INTO muestras VALUES
  (1, 1.5, 10.25, 'abc', 'texto corto', 'un texto sin tope', DATE '2026-01-31', TIME '08:30:00', TRUE),
  (2, 0.1, 99999.99, 'xy', 'otro', 'otro texto', DATE '2020-02-29', TIME '23:59', FALSE);

SELECT entero, flotante, exacto, fijo, variable, libre, fecha, hora, bandera
FROM muestras
ORDER BY entero;

DROP TABLE muestras;
