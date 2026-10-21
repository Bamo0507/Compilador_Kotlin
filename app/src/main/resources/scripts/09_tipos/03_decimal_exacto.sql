-- NOMBRE: DECIMAL exacto contra FLOAT inexacto
-- ESPERADO: sin errores
-- FILAS: 1

-- FLOAT es IEEE-754: 0.1 + 0.2 no da exactamente 0.3. DECIMAL se respalda con
-- aritmetica exacta, que es lo que se necesita para dinero.

CREATE TABLE montos (
  id      INT PRIMARY KEY,
  exacto  DECIMAL(10,2),
  inexacto FLOAT
);

INSERT INTO montos VALUES (1, 0.10, 0.1), (2, 0.20, 0.2);

-- La suma exacta da 0.30 y la comparacion se cumple.
SELECT SUM(exacto) AS total FROM montos;

-- Multiplicar dos decimales de dos cifras necesita cuatro: quedarse con dos
-- haria que 0.05 * 0.05 diera 0.00.
SELECT id, exacto * exacto AS cuadrado FROM montos ORDER BY id;

-- Al dividir se usan seis decimales de mas, porque 1/3 no termina.
SELECT id, exacto / 3.00 AS tercio FROM montos ORDER BY id;

SELECT COUNT(*) AS filas FROM montos WHERE exacto = 0.10;

DROP TABLE montos;
