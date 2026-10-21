-- NOMBRE: Condicion del JOIN que no es logica
-- ESPERADO: error
-- MENSAJE: la condicion del JOIN espera BOOLEAN
-- LINEA: 18

-- El ON empareja filas, asi que tiene que ser una condicion.

CREATE TABLE deptos (
  id     INT PRIMARY KEY,
  nombre VARCHAR(40) NOT NULL
);

CREATE TABLE empleados (
  id    INT PRIMARY KEY,
  depto INT
);

SELECT e.id FROM empleados e JOIN deptos d ON d.id;
