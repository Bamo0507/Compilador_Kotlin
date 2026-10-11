vtable Caja:
begin_func $main, 32
    cero = 0
    t1 = alloc 16
    t1[0] = 3
    t1[4] = 1
    t1[8] = 2
    t1[12] = 3
    lista = t1
    i = 5
    caja = null
    try L1, e
    if cero != 0 goto L3
    throw "División entre cero (línea 18)"
L3:
    t1 = 10 / cero
    print_i t1
    endtry
    goto L2
L1:
    print_s "división atrapada"
L2:
    try L4, e@23
    if lista != null goto L6
    throw "Acceso a null (línea 24)"
L6:
    t1 = lista[0]
    if i < 0 goto L8
    if i < t1 goto L7
L8:
    throw "Índice fuera de rango (línea 24)"
L7:
    t1 = i * 4
    t1 = t1 + 4
    t1 = lista[t1]
    print_i t1
    endtry
    goto L5
L4:
    print_s "índice atrapado"
L5:
    try L9, e@29
    if caja != null goto L11
    throw "Acceso a null (línea 30)"
L11:
    t1 = caja[4]
    print_i t1
    endtry
    goto L10
L9:
    print_s "null atrapado"
L10:
end_func $main
begin_func Caja.$init, 16
    this[4] = 0
end_func Caja.$init
