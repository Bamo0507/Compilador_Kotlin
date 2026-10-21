-- NOMBRE: Crear un indice
-- ESPERADO: sin errores
-- FILAS: 3

-- El indice guarda el valor de la columna y el byte donde arranca su fila, asi
-- que una busqueda deja de recorrer el archivo entero.

CREATE TABLE carros (
  id    INT PRIMARY KEY,
  marca VARCHAR(20) NOT NULL,
  anio  INT
);

INSERT INTO carros VALUES
  (1, 'Toyota', 2020),
  (2, 'Mazda', 2019),
  (3, 'Toyota', 2021),
  (4, 'Mazda', 2022),
  (5, 'Toyota', 2018);

CREATE INDEX idx_carros_marca ON carros(marca);

SELECT id, marca, anio FROM carros WHERE marca = 'Toyota' ORDER BY id;

DROP INDEX idx_carros_marca;
DROP TABLE carros;
