-- NOMBRE: AUTOINCREMENT en una columna de texto
-- ESPERADO: error
-- MENSAJE: AUTOINCREMENT solo aplica a INT
-- LINEA: 9

-- El siguiente numero solo tiene sentido sobre un numero.

CREATE TABLE cosas (
  codigo VARCHAR(10) PRIMARY KEY AUTOINCREMENT
);
