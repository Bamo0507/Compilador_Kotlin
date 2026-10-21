-- NOMBRE: Borrar un indice
-- ESPERADO: sin errores
-- FILAS: 2

-- Borrar el indice no toca los datos: la misma consulta sigue dando lo mismo,
-- solo que recorriendo el archivo.

CREATE TABLE carros (
  id    INT PRIMARY KEY,
  marca VARCHAR(20) NOT NULL,
  anio  INT
);

INSERT INTO carros VALUES (1, 'Toyota', 2020), (2, 'Mazda', 2019), (3, 'Mazda', 2022);

CREATE INDEX idx_carros_anio ON carros(anio);
DROP INDEX idx_carros_anio;

SELECT id, marca FROM carros WHERE marca = 'Mazda' ORDER BY id;

DROP TABLE carros;
