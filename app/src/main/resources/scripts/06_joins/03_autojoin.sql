-- NOMBRE: Autojoin: la tabla consigo misma
-- ESPERADO: sin errores
-- FILAS: 3

-- La misma tabla dos veces en el FROM necesita dos alias: sin ellos no habria
-- como decir de cual de las dos copias es cada columna.

CREATE TABLE personal (
  id     INT PRIMARY KEY,
  nombre VARCHAR(60) NOT NULL,
  jefe   INT
);

INSERT INTO personal VALUES
  (1, 'Ana', NULL),
  (2, 'Bruno', 1),
  (3, 'Cora', 1),
  (4, 'Dan', 2);

SELECT e.nombre AS empleado, j.nombre AS jefe
FROM personal e
JOIN personal j ON j.id = e.jefe
ORDER BY e.nombre;

DROP TABLE personal;
