-- NOMBRE: Nombre de indice repetido
-- ESPERADO: error
-- MENSAJE: ya existe un indice llamado 'idx_depto'
-- LINEA: 17

-- El nombre es con lo que se borra despues, asi que no puede repetirse.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

CREATE INDEX idx_depto ON empleados(depto);
CREATE INDEX idx_depto ON empleados(salario);
