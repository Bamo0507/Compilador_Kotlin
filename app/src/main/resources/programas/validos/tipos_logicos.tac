begin_func $main, 24
    a = true
    b = false
    if a goto L3
    ifFalse b goto L1
L3:
    t1 = true
    goto L2
L1:
    t1 = false
L2:
    print_b t1
    ifFalse a goto L4
    ifFalse b goto L4
    t1 = true
    goto L5
L4:
    t1 = false
L5:
    print_b t1
    t1 = ! a
    print_b t1
end_func $main
