-- NOMBRE: Comparar texto con numero
-- ESPERADO: error
-- MENSAJE: no se puede aplicar
-- LINEA: 17

-- Entre familias distintas no hay conversion implicita: un numero no se
-- convierte solo en texto para que la comparacion cuadre.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre FROM empleados WHERE nombre > 10;
