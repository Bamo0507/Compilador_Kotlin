begin_func $main, 40
    t1 = alloc 12
    t1[0] = 1
    t1[4] = 4
    t2 = alloc 16
    t1[8] = t2
    t2[0] = 1
    potencias = t1
    i = 0
L1:
    if potencias != null goto L3
    throw "Acceso a null (línea 11)"
L3:
    t1 = potencias[0]
    if t1 >= 4 goto L2
    t1 = potencias
    if potencias != null goto L4
    throw "Acceso a null (línea 12)"
L4:
    t2 = potencias[0]
    if i < 0 goto L6
    if i < t2 goto L5
L6:
    throw "Índice fuera de rango (línea 12)"
L5:
    t2 = potencias[8]
    t3 = i * 4
    t2 = t2[t3]
    t2 = t2 * 2
    if t1 != null goto L7
    throw "Acceso a null (línea 12)"
L7:
    push_i t1, t2
    t1 = i + 1
    i = t1
    goto L1
L2:
    j = 0
L8:
    t1 = j
    if potencias != null goto L11
    throw "Acceso a null (línea 16)"
L11:
    t2 = potencias[0]
    if t1 >= t2 goto L10
    if potencias != null goto L12
    throw "Acceso a null (línea 17)"
L12:
    t1 = potencias[0]
    if j < 0 goto L14
    if j < t1 goto L13
L14:
    throw "Índice fuera de rango (línea 17)"
L13:
    t1 = potencias[8]
    t2 = j * 4
    t1 = t1[t2]
    print_i t1
L9:
    t1 = j + 1
    j = t1
    goto L8
L10:
    if potencias != null goto L15
    throw "Acceso a null (línea 19)"
L15:
    t1 = potencias[0]
    print_i t1
end_func $main
