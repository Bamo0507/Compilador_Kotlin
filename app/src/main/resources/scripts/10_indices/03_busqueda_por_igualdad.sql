-- NOMBRE: Buscar por igualdad con indice
-- ESPERADO: sin errores
-- FILAS: 1

-- La clave primaria trae su indice sin que nadie lo pida, asi que `WHERE id = 3`
-- salta directo a esa fila.

CREATE TABLE piezas (
  id     INT PRIMARY KEY,
  codigo VARCHAR(10) UNIQUE,
  stock  INT
);

INSERT INTO piezas VALUES
  (1, 'AA-1', 10),
  (2, 'BB-2', 0),
  (3, 'CC-3', 45),
  (4, 'DD-4', 7);

SELECT id, codigo, stock FROM piezas WHERE id = 3;

DROP TABLE piezas;
