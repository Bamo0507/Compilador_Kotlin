// NOMBRE: Funciones: acceso a variables de dos niveles arriba
// SALIDA: 22

// interna lee base, que vive dos registros arriba: en el TAC es base^2. hermana
// llama a media, su hermana, y le pasa el enlace de acceso subiendo un nivel.
function externa(): integer {
  let base: integer = 10;

  function media(): integer {
    function interna(): integer {
      return base + 1;
    }
    return interna();
  }

  function hermana(): integer {
    return media() * 2;
  }

  return hermana();
}

print(externa());
