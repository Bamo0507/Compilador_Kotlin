-- NOMBRE: Nombre reservado para los automaticos
-- ESPERADO: error
-- MENSAJE: es un nombre reservado
-- LINEA: 16

-- Los nombres pk_ y uq_ los usa el DBMS para los indices que crea solo.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

CREATE INDEX pk_empleados ON empleados(depto);
