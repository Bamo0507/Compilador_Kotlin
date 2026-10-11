begin_func $main, 24
    t1 = call externa, 0
    print_i t1
end_func $main
begin_func externa, 32
    base = 10
    t1 = call externa.hermana, 0, ^0
    return t1
end_func externa
begin_func externa.media, 24
    t1 = call externa.media.interna, 0, ^0
    return t1
end_func externa.media
begin_func externa.hermana, 24
    t1 = call externa.media, 0, ^1
    t1 = t1 * 2
    return t1
end_func externa.hermana
begin_func externa.media.interna, 24
    t1 = base^2 + 1
    return t1
end_func externa.media.interna
