begin_func $main, 32
    t1 = alloc 20
    t1[0] = 4
    t1[4] = 50
    t1[8] = 90
    t1[12] = 100
    t1[16] = 70
    notas = t1
    $lista = notas
    if $lista != null goto L1
    throw "Acceso a null (línea 6)"
L1:
    $i = 0
L2:
    t1 = $lista[0]
    if $i >= t1 goto L4
    t1 = $i * 4
    t1 = t1 + 4
    n = $lista[t1]
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
