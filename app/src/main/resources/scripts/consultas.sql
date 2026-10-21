-- NOMBRE: Consultas sobre la demo
--
-- Solo consulta, asi que se puede correr las veces que haga falta. Necesita que
-- demo_rejilla.sql haya corrido antes, que es quien crea las tablas.

-- Una rejilla ancha: seis columnas, con los numeros y la fecha de cada empleado.
SELECT id, nombre, puesto, salario, ingreso, activo FROM empleados;

-- El nulo contra la cadena vacia, que es la distincion del ticket.
SELECT id, nombre, puesto FROM empleados WHERE id > 4;

-- Un filtro que no casa con nada: "sin resultados", no una rejilla en blanco.
SELECT id, nombre FROM empleados WHERE salario > 100000.00;

-- Una columna calculada sin AS, titulada por su posicion.
SELECT salario * 12 FROM empleados WHERE activo = TRUE;

-- Agregacion por grupo.
SELECT puesto, COUNT(*) AS cuantos, AVG(salario) AS promedio
FROM empleados
GROUP BY puesto;

-- Un JOIN de las dos tablas.
SELECT e.nombre, v.folio, v.monto
FROM empleados e
JOIN ventas v ON v.empleado = e.id;

-- Una subconsulta: quien vendio mas que el promedio.
SELECT nombre FROM empleados
WHERE id IN (SELECT empleado FROM ventas WHERE monto > 20000.00);
