-- NOMBRE: Insertar nombrando las columnas
-- ESPERADO: sin errores
-- FILAS: 3

-- Lo que no se nombra lo rellena el DBMS: el AUTOINCREMENT pone el siguiente
-- numero, el DEFAULT pone su valor, y el resto queda nulo.

CREATE TABLE tareas (
  id       INT PRIMARY KEY AUTOINCREMENT,
  titulo   VARCHAR(60) NOT NULL,
  estado   VARCHAR(20) DEFAULT 'pendiente',
  vence    DATE
);

INSERT INTO tareas (titulo) VALUES ('Escribir el informe');
INSERT INTO tareas (titulo, estado) VALUES ('Revisar el codigo', 'en curso');
INSERT INTO tareas (titulo, vence) VALUES ('Entregar', DATE '2026-11-30');

SELECT id, titulo, estado, vence FROM tareas ORDER BY id;

DROP TABLE tareas;
