-- NOMBRE: Dos claves primarias
-- ESPERADO: error
-- MENSAJE: declara dos claves primarias
-- LINEA: 8

-- La clave primaria es una sola por tabla: es la que identifica la fila.

CREATE TABLE cosas (
  id    INT PRIMARY KEY,
  clave VARCHAR(10) PRIMARY KEY
);
