begin_func $main, 24
    t1 = call crearContador, 0
    print_i t1
end_func $main
begin_func crearContador, 32
    cuenta = 41
    t1 = call crearContador.siguiente, 0, ^0
    return t1
end_func crearContador
begin_func crearContador.siguiente, 24
    t1 = cuenta^1 + 1
    return t1
end_func crearContador.siguiente
