// ESPERADO: linea 5, "mismatched input ';'"

// La gramatica EXIGE el inicializador de una const, asi que este caso lo
// atrapa el parser y no el analizador semantico.
const PI: integer;
