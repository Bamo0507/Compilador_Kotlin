-- NOMBRE: Indice de varias columnas
-- ESPERADO: error
-- MENSAJE: los indices son de una sola columna
-- LINEA: 17

-- La gramatica acepta la lista a proposito: rechazarla aqui permite un
-- mensaje que explica, en vez de un 'se esperaba )'.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

CREATE INDEX idx_x ON empleados(depto, salario);
