-- NOMBRE: Demo de la rejilla de resultados
--
-- Recorre todo lo que el ticket 9.1 tiene que mostrar: las lineas de resumen de
-- las sentencias que escriben, y las rejillas de las que consultan.
--
-- Para volver a correrlo hay que borrar la carpeta app/data primero: un
-- CREATE TABLE sobre una tabla que ya existe es un error, y por la decision 5 un
-- error corta el script y deja el directorio intacto.

-- ── Lo que escribe: cada una deja una linea de resumen ───────────────────

CREATE TABLE empleados (
  id       INT PRIMARY KEY,
  nombre   VARCHAR(60) NOT NULL,
  puesto   VARCHAR(30),
  salario  DECIMAL(10,2),
  ingreso  DATE,
  activo   BOOLEAN
);

CREATE TABLE ventas (
  folio    INT PRIMARY KEY,
  empleado INT REFERENCES empleados(id),
  monto    DECIMAL(10,2)
);

-- Seis filas de una sola sentencia. Fijate en las dos ultimas: una tiene el
-- puesto en NULL y la otra en cadena vacia. La rejilla las tiene que mostrar
-- DISTINTO, que es la razon de ser de este ticket.
INSERT INTO empleados VALUES
  (1, 'Ana Lucia Morales',                      'Gerente',   18500.00, DATE '2021-03-15', TRUE),
  (2, 'Beto Ramirez',                           'Analista',   9800.50, DATE '2023-07-01', TRUE),
  (3, 'Maria Fernanda de los Angeles Quintanilla', 'Desarrolladora', 12000.00, DATE '2022-11-20', TRUE),
  (4, 'Diego Castillo',                         'Analista',   9800.50, DATE '2024-01-08', FALSE),
  (5, 'Elena Paz',                              NULL,         7250.75, DATE '2025-05-30', TRUE),
  (6, 'Franco Ruiz',                            '',           7250.75, DATE '2025-09-12', FALSE);

INSERT INTO ventas VALUES
  (100, 1, 45000.00),
  (101, 2, 12500.00),
  (102, 2,  8300.00),
  (103, 3, 31000.00);

UPDATE empleados SET salario = 19500.00 WHERE id = 1;

DELETE FROM ventas WHERE monto < 10000.00;

CREATE INDEX idx_puesto ON empleados(puesto);

-- ── Lo que consulta: cada una deja su rejilla ────────────────────────────

-- 1. Seis columnas y seis filas. Los numeros y las fechas a la derecha, el
--    texto a la izquierda. El nombre largo se recorta con elipsis.
SELECT id, nombre, puesto, salario, ingreso, activo FROM empleados;

-- 2. La prueba del nulo: la fila 5 trae NULL y la 6 trae '', y se ven distinto.
SELECT id, nombre, puesto FROM empleados WHERE id > 4;

-- 3. Una columna calculada sin AS: la rejilla la titula por su posicion.
SELECT salario * 12 FROM empleados WHERE activo = TRUE;

-- 4. Con AS si tiene nombre, y el tipo sale en el encabezado.
SELECT nombre AS empleado, salario * 12 AS anual FROM empleados WHERE id <= 2;

-- 5. Una agregacion por grupo.
SELECT puesto, COUNT(*) AS cuantos, AVG(salario) AS promedio
FROM empleados
GROUP BY puesto;

-- 6. Un JOIN, para ver dos tablas en una sola rejilla.
SELECT e.nombre, v.folio, v.monto
FROM empleados e
JOIN ventas v ON v.empleado = e.id;

-- 7. Sin filas: la rejilla dice "sin resultados" en vez de quedarse en blanco.
SELECT id, nombre FROM empleados WHERE salario > 100000.00;
