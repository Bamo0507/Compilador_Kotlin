vtable Animal: Animal.hablar
vtable Perro: Perro.hablar
begin_func $main, 32
    t1 = alloc 8
    t1[0] = vtable.Perro
    param t1
    call Perro.$init, 1
    param t1
    param "Toby"
    call Animal.constructor, 2
    perro = t1
    if perro != null goto L1
    throw "Acceso a null (línea 35)"
L1:
    t1 = perro[0]
    t1 = t1[0]
    param perro
    t1 = call t1, 1
    print_s t1
    t1 = alloc 16
    t1[0] = 3
    t1[4] = 90
    t1[8] = 85
    t1[12] = 100
    notas = t1
    $lista = notas
    if $lista != null goto L2
    throw "Acceso a null (línea 38)"
L2:
    $i = 0
L3:
    t1 = $lista[0]
    if $i >= t1 goto L5
    t1 = $i * 4
    t1 = t1 + 4
    nota = $lista[t1]
    if nota >= 60 goto L6
    goto L4
L6:
    print_i nota
L4:
    $i = $i + 1
    goto L3
L5:
    param 5
    t1 = call factorial, 1
    print_i t1
    print_i 13
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
begin_func factorial, 40
    if n > 1 goto L7
    return 1
L7:
    t1 = n
    t2 = n - 1
    param t2
    t2 = call factorial, 1
    t1 = t1 * t2
    return t1
end_func factorial
