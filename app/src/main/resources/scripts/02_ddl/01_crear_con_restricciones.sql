-- NOMBRE: Crear con las siete restricciones
-- ESPERADO: sin errores
-- FILAS: 2

-- PRIMARY KEY, NOT NULL, NULL, UNIQUE, AUTOINCREMENT, DEFAULT y REFERENCES, las
-- siete que acepta la gramatica, en dos tablas.

CREATE TABLE paises (
  id     INT PRIMARY KEY,
  codigo CHAR(2) UNIQUE NOT NULL
);

CREATE TABLE clientes (
  id       INT PRIMARY KEY AUTOINCREMENT,
  nombre   VARCHAR(60) NOT NULL,
  correo   VARCHAR(80) UNIQUE,
  pais     INT REFERENCES paises(id),
  nota     TEXT NULL,
  credito  DECIMAL(8,2) DEFAULT 500.00
);

INSERT INTO paises VALUES (1, 'GT'), (2, 'MX');

INSERT INTO clientes (nombre, correo, pais) VALUES
  ('Ana', 'ana@ejemplo.com', 1),
  ('Beto', 'beto@ejemplo.com', 2);

-- El credito sale del DEFAULT y el id del AUTOINCREMENT.
SELECT id, nombre, correo, pais, nota, credito FROM clientes ORDER BY id;

DROP TABLE clientes;
DROP TABLE paises;
