vtable Medida:
begin_func $main, 24
    t1 = alloc 12
    t1[0] = vtable.Medida
    param t1
    call Medida.$init, 1
    medida = t1
    if medida != null goto L1
    throw "Acceso a null (línea 13)"
L1:
    t1 = medida[4]
    print_i t1
    if medida != null goto L2
    throw "Acceso a null (línea 14)"
L2:
    t1 = medida[8]
    print_i t1
end_func $main
begin_func Medida.$init, 24
    this[4] = 10
    t1 = this[4]
    t1 = t1 * 2
    this[8] = t1
end_func Medida.$init
