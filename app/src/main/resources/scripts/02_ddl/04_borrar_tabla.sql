-- NOMBRE: Borrar una tabla
-- ESPERADO: sin errores
-- FILAS: 1

-- El orden importa: primero la que referencia y despues la referida, porque una
-- tabla con referencias vivas no se puede borrar.

CREATE TABLE marcas (
  id     INT PRIMARY KEY,
  nombre VARCHAR(40) NOT NULL
);

CREATE TABLE carros (
  id    INT PRIMARY KEY,
  marca INT REFERENCES marcas(id)
);

INSERT INTO marcas VALUES (1, 'Toyota');
INSERT INTO carros VALUES (1, 1);

SELECT id, marca FROM carros;

DROP TABLE carros;
DROP TABLE marcas;
