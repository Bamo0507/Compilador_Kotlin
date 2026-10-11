begin_func $main, 24
    param 2
    t1 = call nombre, 1
    print_s t1
    param 7
    t1 = call nombre, 1
    print_s t1
    saludo = "hola"
    if saludo !=s "adios" goto L2
    print_s "adios"
    goto L1
L2:
    if saludo !=s "hola" goto L3
    print_s "hola"
    goto L1
L3:
L1:
end_func $main
begin_func nombre, 24
    resultado = ""
    if n != 1 goto L5
    resultado = "uno"
    goto L4
L5:
    if n != 2 goto L6
    resultado = "dos"
    goto L4
L6:
    resultado = "otro"
L4:
    return resultado
end_func nombre
