begin_func $main, 24
    x = 20
    if x <= 10 goto L1
    print_s "mayor"
    goto L2
L1:
    print_s "menor o igual"
L2:
    i = 0
L3:
    if i >= 5 goto L4
    t1 = i + 1
    i = t1
    goto L3
L4:
    print_i i
L5:
    t1 = i - 1
    i = t1
L6:
    if i > 0 goto L5
L7:
    print_i i
    j = 0
L8:
    if j >= 3 goto L10
    print_i j
L9:
    t1 = j + 1
    j = t1
    goto L8
L10:
end_func $main
