-- NOMBRE: Buscar por rango con indice
-- ESPERADO: sin errores
-- FILAS: 2

-- El indice es un arbol ordenado, no una tabla de dispersion: por eso tambien
-- sirve para `>` y no solo para `=`.

CREATE TABLE lecturas (
  id    INT PRIMARY KEY,
  grado INT
);

INSERT INTO lecturas VALUES (1, 10), (2, 25), (3, 31), (4, 18);

CREATE INDEX idx_lecturas_grado ON lecturas(grado);

SELECT id, grado FROM lecturas WHERE grado > 20 ORDER BY id;

DROP INDEX idx_lecturas_grado;
DROP TABLE lecturas;
