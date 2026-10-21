-- NOMBRE: FROM mal escrito
-- ESPERADO: error
-- MENSAJE: missing 'FROM'
-- LINEA: 17

-- La etapa sintactica es la unica que corta: sin arbol no hay nada que
-- analizar, asi que el resto del script ni se mira.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

SELECT nombre FORM empleados;
