# MineLatino AFK Farm 0.1.0-alpha.3

- Seguimiento suave de recorridos mediante punto adelantado en lugar de perseguir cada muestra.
- Resincronización limitada cuando el jugador sobrepasa un punto, sin saltarse el recorrido entero.
- Pulsos de salto breves con cooldown para evitar saltos continuos.
- Detección de progreso real y cancelación explicativa tras seis segundos bloqueado.
- Grabaciones menos densas para reducir oscilaciones de cámara.
- Diagnóstico de objetivos cercanos con ID real, distancia, selección y línea de visión.
- Aviso específico cuando el cliente recibe una entidad como jugador o disguise; usuarios reales
  permanecen siempre excluidos.
