begin_func $main, 48
    t1 = alloc 12
    t1[0] = 3
    t1[4] = 4
    t2 = alloc 16
    t1[8] = t2
    t2[0] = 90
    t2[4] = 85
    t2[8] = 100
    notas = t1
    if notas != null goto L1
    throw "Acceso a null (línea 7)"
L1:
    t1 = notas[0]
    if 0 < t1 goto L2
    throw "Índice fuera de rango (línea 7)"
L2:
    t1 = notas[8]
    t1 = t1[0]
    print_i t1
    t1 = alloc 12
    t1[0] = 2
    t1[4] = 4
    t2 = alloc 16
    t1[8] = t2
    t3 = alloc 12
    t3[0] = 2
    t3[4] = 4
    t4 = alloc 16
    t3[8] = t4
    t4[0] = 1
    t4[4] = 2
    t2[0] = t3
    t3 = alloc 12
    t3[0] = 2
    t3[4] = 4
    t4 = alloc 16
    t3[8] = t4
    t4[0] = 3
    t4[4] = 4
    t2[4] = t3
    matriz = t1
    if matriz != null goto L3
    throw "Acceso a null (línea 10)"
L3:
    t1 = matriz[0]
    if 1 < t1 goto L4
    throw "Índice fuera de rango (línea 10)"
L4:
    t1 = matriz[8]
    t1 = t1[4]
    if t1 != null goto L5
    throw "Acceso a null (línea 10)"
L5:
    t2 = t1[0]
    if 0 < t2 goto L6
    throw "Índice fuera de rango (línea 10)"
L6:
    t2 = t1[8]
    t1 = t2[0]
    print_i t1
    if notas != null goto L7
    throw "Acceso a null (línea 13)"
L7:
    t1 = notas[0]
    if 1 < t1 goto L8
    throw "Índice fuera de rango (línea 13)"
L8:
    t1 = notas[8]
    t1[4] = 100
    if notas != null goto L9
    throw "Acceso a null (línea 14)"
L9:
    t1 = notas[0]
    if 1 < t1 goto L10
    throw "Índice fuera de rango (línea 14)"
L10:
    t1 = notas[8]
    t1 = t1[4]
    print_i t1
end_func $main
