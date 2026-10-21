-- NOMBRE: DEFAULT de un tipo que no cuadra
-- ESPERADO: error
-- MENSAJE: el valor por omision de
-- LINEA: 10

-- El valor por omision tiene que caber en la columna que lo declara.

CREATE TABLE cosas (
  id     INT PRIMARY KEY,
  cuenta INT DEFAULT 'cero'
);
