begin_func $main, 32
    n = 7
    t2 = n % 2
    if t2 != 0 goto L1
    t1 = "par"
    goto L2
L1:
    t1 = "impar"
L2:
    paridad = t1
    if n <= 0 goto L3
    if n >= 10 goto L3
    t1 = true
    goto L4
L3:
    t1 = false
L4:
    enRango = t1
    if n <= 5 goto L5
    t1 = 1.5
    goto L6
L5:
    t1 = 2.5
L6:
    f = t1
    print_s paridad
    print_b enRango
    print_f f
end_func $main
