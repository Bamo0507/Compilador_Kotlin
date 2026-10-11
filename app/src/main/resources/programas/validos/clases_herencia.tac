vtable Animal: Animal.hablar
vtable Perro: Perro.hablar
begin_func $main, 24
    t1 = alloc 8
    t1[0] = vtable.Perro
    param t1
    call Perro.$init, 1
    param t1
    param "Toby"
    call Animal.constructor, 2
    perro = t1
    if perro != null goto L1
    throw "Acceso a null (línea 26)"
L1:
    t1 = perro[0]
    t1 = t1[0]
    param perro
    t1 = call t1, 1
    print_s t1
    t1 = alloc 8
    t1[0] = vtable.Animal
    param t1
    call Animal.$init, 1
    param t1
    param "Rex"
    call Animal.constructor, 2
    otro = t1
    if otro != null goto L2
    throw "Acceso a null (línea 29)"
L2:
    t1 = otro[0]
    t1 = t1[0]
    param otro
    t1 = call t1, 1
    print_s t1
    t1 = alloc 8
    t1[0] = vtable.Perro
    param t1
    call Perro.$init, 1
    param t1
    param "Toby"
    call Animal.constructor, 2
    comoAnimal = t1
    if comoAnimal != null goto L3
    throw "Acceso a null (línea 34)"
L3:
    t1 = comoAnimal[0]
    t1 = t1[0]
    param comoAnimal
    t1 = call t1, 1
    print_s t1
end_func $main
begin_func Animal.$init, 16
    this[4] = ""
end_func Animal.$init
begin_func Animal.constructor, 24
    this[4] = nombre
end_func Animal.constructor
begin_func Animal.hablar, 32
    t1 = this[4]
    t1 = t1 concat " hace ruido."
    return t1
end_func Animal.hablar
begin_func Perro.$init, 16
    param this
    call Animal.$init, 1
end_func Perro.$init
begin_func Perro.hablar, 32
    t1 = this[4]
    t1 = t1 concat " ladra."
    return t1
end_func Perro.hablar
