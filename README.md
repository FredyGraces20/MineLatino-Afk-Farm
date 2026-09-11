# MineLatino AFK Farm

Mod cliente de automatización controlada para MineLatino. La configuración se abre desde el
botón **AFK Farm** del menú de pausa y se guarda localmente en
`config/minelatino-afk-farm/afk-farm.json`.

## Compatibilidad

| Minecraft | Fabric | Forge |
| --- | --- | --- |
| 1.21.4 | Sí | Sí |
| 1.21.11 | Sí | Sí |

Requiere Java 21. Todos los módulos vienen desactivados por defecto.

## Flujo

1. Espera a que mundo, jugador y conexión estén listos durante 20 ticks consecutivos.
2. Aplica la espera posterior a la conexión y ejecuta hasta 10 comandos configurados.
3. Conserva el estado pendiente si un `/warp` cambia al jugador de host y espera a que el
   nuevo mundo vuelva a estar listo.
4. Espera el intervalo de movimiento y reproduce el recorrido grabado punto por punto,
   incluyendo saltos y cambios de altura.
5. Al terminar, busca únicamente mobs o animales seleccionados desde la lista visual.

Los recorridos se graban caminando desde la pestaña **Recorrido**, pueden guardarse con nombre,
reutilizarse y eliminarse. La versión 2 del JSON migra automáticamente configuraciones anteriores.

Abrir otra pantalla, usar las teclas de movimiento o cancelar desde el menú detiene la
secuencia y libera las teclas simuladas.

## Seguridad del ataque automático

El ataque solo puede activarse en `play.minelatino.com`. Además exige simultáneamente:

- módulo de ataque habilitado;
- categoría e identificador elegidos en el selector visual;
- objetivo vivo, cercano y con línea de visión;
- nunca jugadores ni mascotas domesticadas;
- fuerza de ataque real `>= 0.95`;
- intervalo interno mínimo de 5 ticks (máximo 4 intentos por segundo).

La frecuencia no existe en la interfaz, el JSON ni el backend. Solo puede cambiarse publicando
una nueva compilación oficial. Consulta [docs/attack-policy.md](docs/attack-policy.md).

## Prueba de tiempos web

El panel administrativo incluye **Launcher → AFK Farm**. Allí se pueden validar hasta 10
comandos y simular en tiempo real las esperas posterior a la conexión, entre comandos y antes
del recorrido. Es un simulador seguro: no ejecuta comandos ni sobrescribe configuraciones de
jugadores conectados.

## Compilar

```powershell
$env:JAVA_HOME = 'ruta-a-java-21'
./gradlew.bat :common:test :fabric:build -PmcVersion=1.21.4 --no-daemon
./forge/gradlew.bat -p forge build -PmcVersion=1.21.4 --no-daemon
```

Cambie `1.21.4` por `1.21.11` para la otra versión. Para no escribir artefactos en una carpeta
sincronizada puede definir `MINELATINO_AFK_BUILD_ROOT`.

## Distribución

`deploy.ps1` compila los cuatro JAR, publica una versión inmutable de GitHub y genera
`mods.json` con nombre, tamaño y SHA-1. El backend del launcher distribuye ese manifiesto y el
launcher verifica el archivo nuevo antes de eliminar versiones anteriores.
