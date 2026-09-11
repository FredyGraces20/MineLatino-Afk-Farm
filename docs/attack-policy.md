# Política interna de ataque

Estas constantes están compiladas dentro de `AfkFarmClient` y deliberadamente no forman parte
de `afk-farm.json` ni de la configuración remota:

| Constante | Valor | Función |
| --- | ---: | --- |
| `MINIMUM_ATTACK_INTERVAL_TICKS` | 5 | Máximo de 4 intentos por segundo a 20 TPS |
| `REQUIRED_ATTACK_STRENGTH` | 0.95 | Cooldown real mínimo del arma |
| `ATTACK_SEARCH_RADIUS` | 4.5 | Radio máximo de selección local |

Un ataque ocurre únicamente cuando se cumplen a la vez el intervalo fijo y el cooldown real.
Si el arma tarda más, prevalece su cooldown. El backend no tiene ningún campo para cambiar esta
política. Modificarla exige editar el código, compilar y publicar otra versión oficial.

La autorización del servidor también está compilada: el host, ignorando el puerto, debe ser
exactamente `play.minelatino.com`. No se aceptan sufijos, comodines ni valores entregados por un
servidor remoto.
