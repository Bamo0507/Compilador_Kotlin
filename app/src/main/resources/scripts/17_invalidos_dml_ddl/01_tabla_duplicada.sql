-- NOMBRE: Crear una tabla que ya existe
-- ESPERADO: error
-- MENSAJE: la tabla 'empleados' ya existe
-- LINEA: 17

-- El catalogo del analizador avanza con el script, asi que la segunda
-- sentencia ya ve la tabla que creo la primera.

CREATE TABLE empleados (
  id      INT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  depto   INT,
  salario DECIMAL(10,2),
  activo  BOOLEAN
);

CREATE TABLE empleados (id INT PRIMARY KEY);
