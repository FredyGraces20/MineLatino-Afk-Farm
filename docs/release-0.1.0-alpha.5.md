# MineLatino AFK Farm 0.1.0-alpha.5

- Saldo de uso AFK Farm vinculado a la cuenta MineLatino.
- Tiempo restante visible en la pestaña General.
- Inicio bloqueado hasta confirmar saldo positivo con el backend.
- Sesión exclusiva y heartbeat de consumo cada 20 segundos.
- Detención automática al agotar el tiempo o perder la renovación durante 65 segundos.
- Panel administrativo para buscar cuentas, añadir tiempo, establecer saldo o dejarlo en cero.

El backend calcula el consumo. El JAR no contiene credenciales administrativas ni permite que el
cliente declare cuántos segundos ha utilizado.
