begin_func $main, 32
    a = 3
    b = 4
    t1 = a * b
    t1 = t1 + t1
    x = t1
    t1 = a * b
    t2 = call incrementar, 0
    t1 = t1 + t2
    t2 = a * b
    t1 = t1 + t2
    y = t1
    print_i x
    print_i y
end_func $main
begin_func incrementar, 24
    t1 = a + 1
    a = t1
    return 0
end_func incrementar
