-- NOMBRE: Insertar varias filas de una vez
-- ESPERADO: sin errores
-- FILAS: 4

-- Sin nombrar columnas, los valores van en el orden del esquema, que es el
-- mismo orden del CSV.

CREATE TABLE ciudades (
  id        INT PRIMARY KEY,
  nombre    VARCHAR(40) NOT NULL,
  habitantes INT
);

INSERT INTO ciudades VALUES
  (1, 'Guatemala', 1000000),
  (2, 'Quetzaltenango', 180000),
  (3, 'Antigua', 45000),
  (4, 'Coban', 90000);

SELECT id, nombre, habitantes FROM ciudades ORDER BY id;

DROP TABLE ciudades;
