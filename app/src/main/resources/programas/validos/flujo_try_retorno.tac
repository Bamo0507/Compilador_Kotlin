begin_func $main, 24
    param 10
    param 2
    t1 = call dividir, 2
    print_i t1
    param 10
    param 0
    t1 = call dividir, 2
    print_i t1
end_func $main
begin_func dividir, 40
    try L1, error
    if b != 0 goto L3
    throw "División entre cero (línea 8)"
L3:
    t1 = a / b
    endtry
    return t1
    endtry
    goto L2
L1:
    return -1
L2:
end_func dividir
