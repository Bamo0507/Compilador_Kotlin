-- NOMBRE: SUM sobre una columna de texto
-- ESPERADO: error
-- MENSAJE: SUM espera un tipo numerico
-- LINEA: 17

-- Sumar nombres no significa nada. MIN y MAX si aceptan texto, porque el
-- texto si tiene orden.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT SUM(nombre) FROM empleados;
