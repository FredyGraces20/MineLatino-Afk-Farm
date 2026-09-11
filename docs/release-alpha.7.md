# MineLatino AFK Farm 0.1.0-alpha.7

- Añade detección conservadora, completamente del lado del cliente, para entidades artificiales disfrazadas de jugador.
- La detección usa el ciclo de vida de PlayerInfo y no depende del nombre, Unicode, skin ni tipo original del mob.
- Añade la opción independiente «Atacar disguises artificiales», desactivada por defecto.
- Los perfiles estables, el jugador local y cualquier entidad ambigua permanecen excluidos del ataque.
- Limpia toda evidencia al cambiar de mundo o de host para evitar clasificaciones obsoletas.
