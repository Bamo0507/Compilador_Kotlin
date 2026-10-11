vtable Animal: Animal.hablar
vtable Perro: Perro.hablar
begin_func $main, 24
    t1 = alloc 4
    t1[0] = vtable.Animal
    param t1
    call Animal.$init, 1
    a = t1
    if a != null goto L1
    throw "Acceso a null (línea 17)"
L1:
    t1 = a[0]
    t1 = t1[0]
    param a
    t1 = call t1, 1
    print_s t1
    t1 = alloc 4
    t1[0] = vtable.Perro
    param t1
    call Perro.$init, 1
    a = t1
    if a != null goto L2
    throw "Acceso a null (línea 20)"
L2:
    t1 = a[0]
    t1 = t1[0]
    param a
    t1 = call t1, 1
    print_s t1
end_func $main
begin_func Animal.$init, 16
end_func Animal.$init
begin_func Animal.hablar, 24
    return "..."
end_func Animal.hablar
begin_func Perro.$init, 16
    param this
    call Animal.$init, 1
end_func Perro.$init
begin_func Perro.hablar, 24
    return "guau"
end_func Perro.hablar
