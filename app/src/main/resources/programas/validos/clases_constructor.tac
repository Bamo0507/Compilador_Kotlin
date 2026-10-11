vtable Animal: Animal.hablar
begin_func $main, 24
    t1 = alloc 8
    t1[0] = vtable.Animal
    param t1
    call Animal.$init, 1
    param t1
    param "Rex"
    call Animal.constructor, 2
    animal = t1
    if animal != null goto L1
    throw "Acceso a null (línea 18)"
L1:
    t1 = animal[0]
    t1 = t1[0]
    param animal
    t1 = call t1, 1
    print_s t1
    if animal != null goto L2
    throw "Acceso a null (línea 19)"
L2:
    t1 = animal[4]
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
