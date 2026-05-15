# Convención de terminología SLR(1) y LALR(1) en este proyecto

**Fecha**: 2026-05-14
**Estado**: vigente
**Alcance**: módulos `frontend/syntaxAnalyzer/slr1/` y `frontend/syntaxAnalyzer/lalr1/`.

## Resumen

En este proyecto los nombres SLR(1) y LALR(1) **NO siguen la definición del Dragon Book (Aho, Lam, Sethi, Ullman, 2nd ed.)**. Se usan los términos como los emplea el catedrático del curso. Este documento fija la convención para evitar confusión.

## Definiciones del Dragon Book (referencia)

- **SLR(1)** (§4.6.4, Algoritmo 4.46): autómata LR(0) + tabla cuyas reducciones se llenan usando `FOLLOW(A)` como lookahead para cada item completo `[A -> alpha.]`.
- **LR(1) canónico** (§4.7.2, Algoritmo 4.53): autómata cuyos items son `[A -> alpha.beta, a]` con lookahead explícito propagado por closure vía `FIRST(beta a)`.
- **LALR(1)** (§4.7.4, Algoritmo 4.59): autómata LR(1) canónico tras fusionar estados con el mismo core, uniendo lookaheads.

## Definiciones de este proyecto

| Nombre en este proyecto | Equivale en el Dragon Book a |
|---|---|
| **SLR(1)** | LR(1) canónico (§4.7.2) |
| **LALR(1)** | LALR(1) por merge desde LR(1) canónico (§4.7.4) |

Concretamente:

### SLR(1) (este proyecto)

- Items: `[A -> alpha.beta, a]` con lookahead.
- Closure: propaga lookaheads vía `FIRST(beta a)` como en Algoritmo 4.53 del Dragon Book.
- Goto: idéntico a LR(0) en estructura, opera sobre items con lookahead.
- Tabla: `ACTION[i, a] = Reduce(A -> alpha)` si y solo si `[A -> alpha., a]` está en `I_i`. **No usa `FOLLOW(A)`**.
- **No se fusionan** estados.

### LALR(1) (este proyecto)

- Idéntico a SLR(1) **más un paso adicional**: fusionar todos los estados que comparten el mismo core (mismo conjunto de items ignorando lookaheads), uniendo los lookaheads de los items correspondientes.
- Tras la fusión, la tabla se construye con el mismo algoritmo que SLR(1).

## Implicaciones prácticas

1. **`FollowSetComputer` sigue existiendo** porque se usa en el módulo LL(1) (Algoritmo 4.31 del Dragon Book). Pero **no** se invoca desde `SLR1TableBuilder` ni `LALR1TableBuilder`.
2. **No hay módulo LR(0) separado** porque la construcción del autómata siempre lleva lookaheads. Lo que en el Dragon Book sería el autómata LR(0) puro se vería en este proyecto como "el core del autómata SLR(1) ignorando lookaheads", útil sólo como vista de visualización.
3. **No hay módulo LR(1) separado** del módulo SLR(1). Lo que el Dragon Book llama LR(1) canónico es lo que este proyecto llama SLR(1).
4. La diferencia entre SLR(1) y LALR(1) en este proyecto es **exactamente** el paso de merge. Comparten todo el código previo.

## Por qué se mantiene esta convención

- Es la terminología que usa el catedrático y bajo la cual se evalúa el proyecto.
- La equivalencia con Dragon Book queda documentada acá para que cualquier colaborador o agente que entre al proyecto pueda mapear los nombres a la literatura estándar sin perder tiempo.

## Cómo defender la elección frente a alguien que cite el Dragon Book

> "En este proyecto SLR(1) se construye con lookaheads desde el closure (Algoritmo 4.53), no con FOLLOW (Algoritmo 4.46). Es una decisión pedagógica del catedrático para tratar SLR(1) y LALR(1) como variantes del mismo algoritmo, donde la única diferencia es el merge final. La documentación interna (`docs/plans/2026-05-14-slr1-lalr1-terminology.md`) fija esta convención."

## Referencias

- Aho, Lam, Sethi, Ullman. *Compilers: Principles, Techniques, and Tools*, 2nd ed. §4.6.4, §4.7.2, §4.7.4.
