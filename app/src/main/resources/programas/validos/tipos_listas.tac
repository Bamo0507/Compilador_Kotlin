begin_func $main, 32
    t1 = alloc 16
    t1[0] = 3
    t1[4] = 90
    t1[8] = 85
    t1[12] = 100
    notas = t1
    if notas != null goto L1
    throw "Acceso a null (línea 7)"
L1:
    t1 = notas[0]
    if 0 < t1 goto L2
    throw "Índice fuera de rango (línea 7)"
L2:
    t1 = notas[4]
    print_i t1
    t1 = alloc 12
    t1[0] = 2
    t2 = alloc 12
    t2[0] = 2
    t2[4] = 1
    t2[8] = 2
    t1[4] = t2
    t2 = alloc 12
    t2[0] = 2
    t2[4] = 3
    t2[8] = 4
    t1[8] = t2
    matriz = t1
    if matriz != null goto L3
    throw "Acceso a null (línea 10)"
L3:
    t1 = matriz[0]
    if 1 < t1 goto L4
    throw "Índice fuera de rango (línea 10)"
L4:
    t1 = matriz[8]
    if t1 != null goto L5
    throw "Acceso a null (línea 10)"
L5:
    t2 = t1[0]
    if 0 < t2 goto L6
    throw "Índice fuera de rango (línea 10)"
L6:
    t1 = t1[4]
    print_i t1
    if notas != null goto L7
    throw "Acceso a null (línea 13)"
L7:
    t1 = notas[0]
    if 1 < t1 goto L8
    throw "Índice fuera de rango (línea 13)"
L8:
    notas[8] = 100
    if notas != null goto L9
    throw "Acceso a null (línea 14)"
L9:
    t1 = notas[0]
    if 1 < t1 goto L10
    throw "Índice fuera de rango (línea 14)"
L10:
    t1 = notas[8]
    print_i t1
end_func $main
