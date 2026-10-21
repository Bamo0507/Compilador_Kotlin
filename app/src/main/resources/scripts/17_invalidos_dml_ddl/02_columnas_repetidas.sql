-- NOMBRE: Dos columnas con el mismo nombre
-- ESPERADO: error
-- MENSAJE: declara dos columnas llamadas 'id'
-- LINEA: 11

-- Dos columnas con el mismo nombre no se podrian distinguir en ninguna
-- consulta.

CREATE TABLE cosas (
  id INT PRIMARY KEY,
  id VARCHAR(10)
);
