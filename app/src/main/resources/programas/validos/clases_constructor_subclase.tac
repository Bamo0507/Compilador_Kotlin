vtable Animal:
vtable Perro:
begin_func $main, 24
    t1 = alloc 12
    t1[0] = vtable.Perro
    param t1
    call Perro.$init, 1
    param t1
    param "Toby"
    param "labrador"
    call Perro.constructor, 3
    perro = t1
    if perro != null goto L1
    throw "Acceso a null (línea 21)"
L1:
    t1 = perro[4]
    print_s t1
    if perro != null goto L2
    throw "Acceso a null (línea 22)"
L2:
    t1 = perro[8]
    print_s t1
end_func $main
begin_func Animal.$init, 16
    this[4] = ""
end_func Animal.$init
begin_func Animal.constructor, 24
    this[4] = nombre
end_func Animal.constructor
begin_func Perro.$init, 16
    param this
    call Animal.$init, 1
    this[8] = ""
end_func Perro.$init
begin_func Perro.constructor, 24
    this[4] = nombre
    this[8] = raza
end_func Perro.constructor
