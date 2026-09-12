# MineLatino AFK Farm 0.1.0-alpha.9

- Reduce la identificación segura de disguises artificiales de 3 segundos a 1 segundo.
- Conserva el objetivo válido entre ticks para evitar búsquedas completas antes de cada golpe.
- Cambia inmediatamente al siguiente objetivo cuando el actual muere, desaparece o deja de ser válido.
- Prioriza objetivos dentro del alcance real de ataque.
- Muestra estados claros al identificar, acercarse, esperar el cooldown y atacar.
- Mantiene el límite interno fijo de 4 intentos por segundo y el cooldown real del arma.
