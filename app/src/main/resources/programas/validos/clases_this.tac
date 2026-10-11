vtable Mascota: Mascota.cumplir, Mascota.comoSeLlama
begin_func $main, 24
    t1 = alloc 12
    t1[0] = vtable.Mascota
    param t1
    call Mascota.$init, 1
    param t1
    param "Firulais"
    call Mascota.constructor, 2
    mascota = t1
    if mascota != null goto L1
    throw "Acceso a null (línea 25)"
L1:
    t1 = mascota[0]
    t1 = t1[4]
    param mascota
    t1 = call t1, 1
    print_s t1
    if mascota != null goto L2
    throw "Acceso a null (línea 26)"
L2:
    t1 = mascota[0]
    t1 = t1[0]
    param mascota
    param 7
    t1 = call t1, 2
    print_i t1
end_func $main
begin_func Mascota.$init, 16
    this[4] = ""
    this[8] = 0
end_func Mascota.$init
begin_func Mascota.constructor, 24
    this[4] = nombre
    this[8] = 0
end_func Mascota.constructor
begin_func Mascota.cumplir, 40
    t1 = this
    t2 = this[8]
    t2 = t2 + anios
    t1[8] = t2
    t1 = this[8]
    return t1
end_func Mascota.cumplir
begin_func Mascota.comoSeLlama, 32
    t1 = this[4]
    return t1
end_func Mascota.comoSeLlama
