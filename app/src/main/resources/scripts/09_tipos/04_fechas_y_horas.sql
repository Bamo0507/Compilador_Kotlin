-- NOMBRE: Fechas y horas
-- ESPERADO: sin errores
-- FILAS: 2

-- DATE y TIME comparten familia pero no se comparan entre si: son cosas
-- distintas, y dejarlo pasar solo esconderia errores.

CREATE TABLE citas (
  id     INT PRIMARY KEY,
  dia    DATE,
  inicio TIME
);

INSERT INTO citas VALUES
  (1, DATE '2026-01-15', TIME '09:00:00'),
  (2, DATE '2026-02-28', TIME '14:30:00'),
  (3, DATE '2025-12-31', TIME '23:45:00');

SELECT id, dia, inicio FROM citas WHERE dia > DATE '2026-01-01' ORDER BY dia;

SELECT id FROM citas WHERE inicio < TIME '12:00:00' ORDER BY id;

SELECT id, dia FROM citas ORDER BY dia DESC LIMIT 2;

DROP TABLE citas;
