begin_func $main, 32
    t1 = alloc 12
    t1[0] = 0
    t1[4] = 0
    t1[8] = null
    notas = t1
    if notas != null goto L1
    throw "Acceso a null (línea 10)"
L1:
    t1 = notas[0]
    print_i t1
    if notas != null goto L2
    throw "Acceso a null (línea 11)"
L2:
    push_i notas, 10
    if notas != null goto L3
    throw "Acceso a null (línea 12)"
L3:
    push_i notas, 20
    if notas != null goto L4
    throw "Acceso a null (línea 13)"
L4:
    push_i notas, 30
    if notas != null goto L5
    throw "Acceso a null (línea 14)"
L5:
    t1 = notas[0]
    print_i t1
    if notas != null goto L6
    throw "Acceso a null (línea 15)"
L6:
    t1 = notas[0]
    if 2 < t1 goto L7
    throw "Índice fuera de rango (línea 15)"
L7:
    t1 = notas[8]
    t1 = t1[8]
    print_i t1
    t1 = alloc 12
    t1[0] = 1
    t1[4] = 4
    t2 = alloc 32
    t1[8] = t2
    t2[0] = 1.5
    precios = t1
    if precios != null goto L8
    throw "Acceso a null (línea 19)"
L8:
    push_f precios, 2.0
    if precios != null goto L9
    throw "Acceso a null (línea 20)"
L9:
    t1 = precios[0]
    if 1 < t1 goto L10
    throw "Índice fuera de rango (línea 20)"
L10:
    t1 = precios[8]
    t1 = t1[8]
    print_f t1
    copia = notas
    if notas != null goto L11
    throw "Acceso a null (línea 25)"
L11:
    push_i notas, 40
    if notas != null goto L12
    throw "Acceso a null (línea 26)"
L12:
    push_i notas, 50
    if copia != null goto L13
    throw "Acceso a null (línea 27)"
L13:
    t1 = copia[0]
    print_i t1
    if copia != null goto L14
    throw "Acceso a null (línea 28)"
L14:
    t1 = copia[0]
    if 4 < t1 goto L15
    throw "Índice fuera de rango (línea 28)"
L15:
    t1 = copia[8]
    t1 = t1[16]
    print_i t1
end_func $main
