grammar Sql;

// La fuente de verdad del analizador sintactico.
//
// Las palabras clave se escriben SIEMPRE en MAYUSCULAS. SQL no distingue `select`
// de `SELECT`, y eso lo resuelve UpperCaseCharStream: el lexer solo ve mayusculas.
// Una palabra clave escrita aqui en minusculas nunca coincidiria con nada.
//
// Consecuencia: las palabras clave quedan reservadas. Una columna no se puede
// llamar `date`, `key` ni `count`.

script: statement* EOF;

// Tipos -------------------------------------------------------------------

type
  : ('INT' | 'INTEGER')                          # intType
  | 'FLOAT'                                      # floatType
  | ('DECIMAL' | 'NUMERIC') '(' IntegerLit ',' IntegerLit ')'  # decimalType
  | 'CHAR' '(' IntegerLit ')'                     # charType
  | 'VARCHAR' '(' IntegerLit ')'                  # varcharType
  | 'TEXT'                                       # textType
  | 'DATE'                                       # dateType
  | 'TIME'                                       # timeType
  | 'BOOLEAN'                                    # boolType
  ;

// Literales ---------------------------------------------------------------

literal
  : IntegerLit          # intLit
  | DecimalLit         # decimalLit
  | StringLit           # stringLit
  | 'DATE' StringLit    # dateLit
  | 'TIME' StringLit    # timeLit
  | ('TRUE' | 'FALSE') # boolLit
  | 'NULL'             # nullLit
  ;

// Sentencias -------------------------------------------------------------

statement
  : createTable ';'  | alterTable ';' | dropTable ';'
  | insert ';'    | update ';'   | delete ';'
  | query ';'
  ;

// DDL ---------------------------------------------------------------------

createTable
  : 'CREATE' 'TABLE' Identifier '(' columnDefinition (',' columnDefinition)* ')'
  ;

columnDefinition : Identifier type constraint* ;

constraint
  : 'PRIMARY' 'KEY'                                          # rPrimaryKey
  | 'NOT' 'NULL'                                             # rNotNull
  | 'NULL'                                                   # rNull
  | 'UNIQUE'                                                 # rUnique
  | 'AUTOINCREMENT'                                          # rAutoIncrement
  | 'DEFAULT' literal                                        # rDefault
  | 'REFERENCES' Identifier '(' Identifier ')'         # rForeignKey
  ;

alterTable
  : 'ALTER' 'TABLE' Identifier 'ADD' columnDefinition    # alterAdd
  | 'ALTER' 'TABLE' Identifier 'DROP' 'COLUMN' Identifier  # alterDrop
  ;

dropTable : 'DROP' 'TABLE' Identifier ;

// DML ---------------------------------------------------------------------

insert
  : 'INSERT' 'INTO' Identifier ('(' columnList ')')? 'VALUES' valuesRow (',' valuesRow)*
  ;

valuesRow : '(' expression (',' expression)* ')' ;
columnList : Identifier (',' Identifier)* ;

update
  : 'UPDATE' Identifier 'SET' assignment (',' assignment)* ('WHERE' expression)?
  ;

assignment : Identifier '=' expression ;

delete : 'DELETE' 'FROM' Identifier ('WHERE' expression)? ;

// Consultas ---------------------------------------------------------------

query
  : 'SELECT' 'DISTINCT'? selectList
    'FROM' source (join)*
    ('WHERE' expression)?
    ('GROUP' 'BY' expressionList)?
    ('HAVING' expression)?
    ('ORDER' 'BY' orderCriterion (',' orderCriterion)*)?
    ('LIMIT' IntegerLit)?
  ;

selectList
  : '*'                                                # selectAll
  | selectItem (',' selectItem)*         # selectListItems
  ;

selectItem
  : Identifier '.' '*'                              # itemTableAll
  | expression ('AS'? Identifier)?                   # itemExpression
  ;

source
  : Identifier ('AS'? Identifier)?               # tableSource
  | '(' query ')' 'AS'? Identifier               # derivedSource
  ;

join : 'INNER'? 'JOIN' source 'ON' expression ;

orderCriterion : expression ('ASC' | 'DESC')? ;
expressionList : expression (',' expression)* ;

// Expresiones · la torre de precedencia, de la mas debil a la mas fuerte ----

expression      : orExpression ;
orExpression    : andExpression ('OR' andExpression)* ;
andExpression   : notExpression ('AND' notExpression)* ;
notExpression   : 'NOT'? comparisonExpression ;

comparisonExpression
  : additiveExpression (comparisonOp additiveExpression)?       # cmpBinary
  | additiveExpression 'IS' 'NOT'? 'NULL'                      # cmpIsNull
  | additiveExpression 'NOT'? 'IN' '(' query ')'            # cmpInSubquery
  | additiveExpression 'NOT'? 'IN' '(' expressionList ')'    # cmpInList
  | 'EXISTS' '(' query ')'                                # cmpExists
  ;

additiveExpression       : multiplicativeExpression (('+' | '-' | '||') multiplicativeExpression)* ;
multiplicativeExpression: unaryExpression (('*' | '/') unaryExpression)* ;
unaryExpression        : '-'? primaryExpression ;

// `'(' query ')'` va ANTES que `'(' expression ')'`: es lo que hace que una
// subconsulta entre parentesis salga como subconsulta y no como agrupacion.
primaryExpression
  : literal                                            # primLiteral
  | Identifier '.' Identifier                    # primQualifiedColumn
  | Identifier                                      # primColumn
  | aggregate                                         # primAggregate
  | '(' query ')'                                   # primSubquery
  | '(' expression ')'                                  # primParen
  ;

aggregate
  : 'COUNT' '(' '*' ')'                                # agCountAll
  | ('COUNT'|'SUM'|'AVG'|'MIN'|'MAX') '(' 'DISTINCT'? expression ')'  # agFunction
  ;

comparisonOp : '=' | '<>' | '!=' | '<' | '>' | '<=' | '>=' ;

// Lexico ------------------------------------------------------------------

IntegerLit : [0-9]+ ;
DecimalLit : [0-9]+ '.' [0-9]+ ;
// La comilla simple se escapa duplicandola, como dice el estandar: 'dijo ''hola'''
StringLit : '\'' ( ~'\'' | '\'\'' )* '\'' ;
Identifier : [a-zA-Z_][a-zA-Z0-9_]* ;

WS : [ \t\r\n]+ -> skip ;
COMMENT : '--' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;
