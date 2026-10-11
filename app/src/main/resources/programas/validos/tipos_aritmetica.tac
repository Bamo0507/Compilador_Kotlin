begin_func $main, 24
    a = 5
    b = 3
    t1 = b * 2
    t1 = a + t1
    print_i t1
    t1 = a - 1
    print_i t1
    if b != 0 goto L1
    throw "División entre cero (línea 12)"
L1:
    t1 = a % b
    print_i t1
    c = 3.5
    t1 = c +f 1.0
    print_f t1
end_func $main
