begin_func $main, 40
    t1 = alloc 12
    t1[0] = 4
    t1[4] = 4
    t2 = alloc 16
    t1[8] = t2
    t2[0] = 50
    t2[4] = 90
    t2[8] = 100
    t2[12] = 70
    notas = t1
    $lista = notas
    if $lista != null goto L1
    throw "Acceso a null (línea 6)"
L1:
    $i = 0
L2:
    t1 = $lista[0]
    if $i >= t1 goto L4
    t1 = $lista[8]
    t2 = $i * 4
    n = t1[t2]
    if n >= 60 goto L5
    goto L3
L5:
    if n != 100 goto L6
    goto L4
L6:
    print_i n
L3:
    $i = $i + 1
    goto L2
L4:
end_func $main
