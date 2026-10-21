-- NOMBRE: Columna ambigua en un join
-- ESPERADO: error
-- MENSAJE: existe en
-- LINEA: 20

-- Las dos tablas tienen una columna 'nombre', asi que el nombre suelto no
-- alcanza para decir cual es: hay que calificarlo.

CREATE TABLE deptos (
  id     INT PRIMARY KEY,
  nombre VARCHAR(40) NOT NULL
);

CREATE TABLE empleados (
  id     INT PRIMARY KEY,
  nombre VARCHAR(60) NOT NULL,
  depto  INT
);

SELECT nombre FROM empleados e JOIN deptos d ON d.id = e.depto;
