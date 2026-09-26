grammar Sql;

// Minima a proposito. La fase 3 la llena con el DDL, el DML y las consultas.
// Existe desde ahora para que el plugin antlr genere lexer y parser, y el resto
// del build tenga contra que compilar.

script: EOF;

WS: [ \t\r\n]+ -> skip;
