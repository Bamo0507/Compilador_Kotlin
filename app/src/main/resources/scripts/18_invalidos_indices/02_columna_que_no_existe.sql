-- NOMBRE: Indice sobre una columna que no existe
-- ESPERADO: error
-- MENSAJE: no tiene columna 'sueldo'
-- LINEA: 16

-- El indice guarda los valores de una columna concreta.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

CREATE INDEX idx_x ON empleados(sueldo);
