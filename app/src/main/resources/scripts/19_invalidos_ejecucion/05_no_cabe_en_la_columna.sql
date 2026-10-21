-- NOMBRE: Un texto que no cabe en la columna
-- ESPERADO: error
-- MENSAJE: no cabe en VARCHAR
-- LINEA: 16

-- El largo de un literal lo revisa el analizador; el de un valor calculado
-- solo se sabe al evaluarlo.

CREATE TABLE etiquetas (
  id    INT PRIMARY KEY,
  texto VARCHAR(10) NOT NULL
);

INSERT INTO etiquetas VALUES (1, 'corto');

UPDATE etiquetas SET texto = texto || ' pero ya no tan corto' WHERE id = 1;
