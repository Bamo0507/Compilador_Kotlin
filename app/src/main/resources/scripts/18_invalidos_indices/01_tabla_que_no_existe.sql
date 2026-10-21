-- NOMBRE: Indice sobre una tabla que no existe
-- ESPERADO: error
-- MENSAJE: la tabla 'fantasma' no existe
-- LINEA: 17

-- El indice vive en el esquema de su tabla, asi que sin tabla no hay donde
-- ponerlo.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

CREATE INDEX idx_x ON fantasma(id);
