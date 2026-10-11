begin_func $main, 24
    param 5
    t1 = call factorial, 1
    print_i t1
end_func $main
begin_func factorial, 40
    if n > 1 goto L1
    return 1
L1:
    t1 = n
    t2 = n - 1
    param t2
    t2 = call factorial, 1
    t1 = t1 * t2
    return t1
end_func factorial
