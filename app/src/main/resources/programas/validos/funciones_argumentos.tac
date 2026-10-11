begin_func $main, 24
    param 2
    param 3
    t1 = call suma, 2
    print_i t1
    param "Mundo"
    t1 = call saludar, 1
    print_s t1
end_func $main
begin_func suma, 32
    t1 = a + b
    return t1
end_func suma
begin_func saludar, 32
    t1 = "Hola " concat nombre
    return t1
end_func saludar
