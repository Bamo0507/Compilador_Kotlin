-- NOMBRE: Tabla derivada con una columna sin nombre
-- ESPERADO: error
-- MENSAJE: no tiene nombre, use AS
-- LINEA: 17

-- La columna calculada de adentro no tiene nombre, asi que afuera no hay como
-- nombrarla.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT t.id FROM (SELECT id, salario * 12 FROM empleados) AS t;
