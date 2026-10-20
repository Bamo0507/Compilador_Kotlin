begin_func $main, 56
    t1 = alloc 12
    t1[0] = 3
    t1[4] = 4
    t2 = alloc 16
    t1[8] = t2
    t2[0] = 10
    t2[4] = 20
    t2[8] = 30
    notas = t1
    t1 = alloc 12
    t1[0] = 0
    t1[4] = 0
    t1[8] = null
    dobles = t1
    $lista = notas
    if $lista != null goto L1
    throw "Acceso a null (línea 10)"
L1:
    $i = 0
L2:
    t1 = $lista[0]
    if $i >= t1 goto L4
    t1 = $lista[8]
    t2 = $i * 4
    nota = t1[t2]
    t1 = nota * 2
    if dobles != null goto L5
    throw "Acceso a null (línea 11)"
L5:
    push_i dobles, t1
L3:
    $i = $i + 1
    goto L2
L4:
    if dobles != null goto L6
    throw "Acceso a null (línea 14)"
L6:
    t1 = dobles[0]
    print_i t1
    $lista@15 = dobles
    if $lista@15 != null goto L7
    throw "Acceso a null (línea 15)"
L7:
    $i@15 = 0
L8:
    t1 = $lista@15[0]
    if $i@15 >= t1 goto L10
    t1 = $lista@15[8]
    t2 = $i@15 * 4
    doble = t1[t2]
    print_i doble
L9:
    $i@15 = $i@15 + 1
    goto L8
L10:
end_func $main
