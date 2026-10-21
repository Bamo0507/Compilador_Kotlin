-- NOMBRE: Join de tres tablas
-- ESPERADO: sin errores
-- FILAS: 4

-- Cada JOIN agrega una tabla al mismo renglon: el resultado tiene las columnas
-- de las tres, concatenadas en el orden del FROM.

CREATE TABLE paises (
  id     INT PRIMARY KEY,
  nombre VARCHAR(40) NOT NULL
);

CREATE TABLE sedes (
  id     INT PRIMARY KEY,
  nombre VARCHAR(40) NOT NULL,
  pais   INT REFERENCES paises(id)
);

CREATE TABLE personas (
  id     INT PRIMARY KEY,
  nombre VARCHAR(60) NOT NULL,
  sede   INT REFERENCES sedes(id)
);

INSERT INTO paises VALUES (1, 'Guatemala'), (2, 'Mexico');
INSERT INTO sedes VALUES (1, 'Central', 1), (2, 'Norte', 2);
INSERT INTO personas VALUES
  (1, 'Ana', 1), (2, 'Bruno', 1), (3, 'Cora', 2), (4, 'Dan', 2);

SELECT p.nombre AS persona, s.nombre AS sede, q.nombre AS pais
FROM personas p
JOIN sedes s ON s.id = p.sede
JOIN paises q ON q.id = s.pais
ORDER BY p.nombre;

DROP TABLE personas;
DROP TABLE sedes;
DROP TABLE paises;
