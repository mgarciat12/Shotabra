# Shotabra 🥃

Juego de fiesta multijugador para jugar en casa, cada uno desde su móvil.

Cada jugador recibe en secreto un **objetivo** (otro jugador) y una **palabra**. Tienes que conseguir que tu objetivo diga tu palabra sin que se dé cuenta. Cuando la diga, pulsas **«¡La dijo!»**. Si tu objetivo lo confirma, pierde la ronda… y se toma un shot.

- **Backend:** Java 25 y Spring Boot 4.2 (Web MVC, WebSocket/STOMP, Data JPA, Validation, Flyway y Actuator), con PostgreSQL.
- **Frontend:** Thymeleaf, Bootstrap 5.3.9 y Bootstrap Icons, con JavaScript sin frameworks (`static/js/game.js`). Todo se sirve con **WebJars**, sin CDN, así que funciona sin internet.
- **Despliegue:** Dockerfile multi-stage, pensado para Coolify.

---

## Ejecutar en local

Solo necesitas **Docker**. No hace falta tener Java 25 instalado: todo se compila dentro de contenedores.

### Opción A: todo con Docker Compose (lo más rápido)

```sh
docker compose up --build
```

Arranca PostgreSQL 18 y la app (perfil `prod`) en http://localhost:8080.

### Opción B: modo desarrollo (perfil `dev`)

```sh
./scripts/dev.sh
```

Levanta solo el PostgreSQL del compose y ejecuta `mvn spring-boot:run` en un contenedor con JDK 25. Las plantillas no se cachean y los logs de `com.shotabra` salen en DEBUG.

### Tests

```sh
./scripts/mvn.sh verify
```

Ejecuta Maven en un contenedor `maven:3.9-eclipse-temurin-25`. Monta el socket de Docker para que **Testcontainers** pueda arrancar un PostgreSQL real para los tests de integración.

| Test | Qué cubre |
|---|---|
| `DerangementsTest` | El desarreglo nunca asigna a uno mismo y es biyectivo (1000 repeticiones para cada N de 3 a 12). Además forma un único ciclo, sin parejas recíprocas. |
| `SmallRulesTest` | Códigos sin caracteres ambiguos, nombres (límite de 16, caracteres de control, `<>`), elección de palabras. |
| `GameServiceTest` | Transiciones de estado y permisos de host. Palabras sin repetir entre rondas. Nadie puede confirmar una acusación ajena. Dos confirmaciones simultáneas dan **un solo perdedor**. Traspaso de host a los 60 s y limpieza a las 6 h. |
| `PersistenceIntegrationTest` | Migraciones Flyway, semilla de más de 300 palabras y restricciones de la base de datos (un perdedor por ronda, nombre y emoji únicos, nadie es su propio objetivo). |
| `GameApiControllerTest` | El flujo HTTP completo. Cookie HttpOnly. Ningún JSON contiene la palabra de otro jugador. Recursos servidos en local y QR. |
| `WebSocketIntegrationTest` | Cliente STOMP real: cada jugador recibe **solo su tarjeta**, los eventos públicos no llevan palabras y el modal de acusación solo le llega al acusado. |

### Jugar con varios móviles en la misma WiFi

1. Arranca la app en tu ordenador (opción A o B).
2. Averigua la IP local del ordenador: en macOS, `ipconfig getifaddr en0` (o *Ajustes → Wi-Fi → Detalles*). En Linux, `hostname -I`.
3. Abre `http://<IP>:8080` en el móvil del host y crea la party.
4. El resto escanea el **QR** del lobby, o entra en la misma URL y escribe el código de 4 letras.

Si `APP_PUBLIC_URL` está vacía, el QR usa la dirección con la que el host abrió la página. Así, en la WiFi de casa apunta a la IP correcta sin configurar nada. Si no conecta, revisa el firewall del ordenador: debe aceptar conexiones entrantes en el puerto 8080.

> **Nota:** fuera de HTTPS, algunos navegadores desactivan la vibración y el bloqueo de pantalla (*wake lock*). El juego funciona igual. En Coolify, con HTTPS, va todo.

---

## Variables de entorno

| Variable | Por defecto | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (en la imagen Docker) | `dev` o `prod`. |
| `SPRING_DATASOURCE_URL` | — | P. ej. `jdbc:postgresql://postgres:5432/shotabra`. |
| `SPRING_DATASOURCE_USERNAME` | — | Usuario de PostgreSQL. |
| `SPRING_DATASOURCE_PASSWORD` | — | Contraseña de PostgreSQL. |
| `APP_PUBLIC_URL` | *(vacía)* | URL pública que usa el QR, p. ej. `https://shotabra.midominio.com`. Si está vacía, se usa la de la petición. |
| `APP_ALLOWED_ORIGINS` | `*` | Orígenes permitidos en el endpoint STOMP (`/ws`), separados por comas. En producción pon tu dominio, p. ej. `https://shotabra.midominio.com`. |
| `SERVER_PORT` | `8080` | Puerto HTTP. |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` | Opciones de la JVM. |

---

## Desplegar en Coolify

1. **Crea la base de datos:** *+ New → Database → PostgreSQL* (16 o superior) en el mismo proyecto y entorno. Apunta el usuario, la contraseña y la **URL interna** (algo como `postgres://usuario:pass@<uuid>:5432/postgres`).
2. **Crea la aplicación:** *+ New → Application* desde el repositorio Git (público o con GitHub App). Como **Build Pack**, elige **Dockerfile** (la ubicación por defecto, `/Dockerfile`, es correcta).
3. **Configura las variables de entorno** en *Environment Variables*:
   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://<host-interno-de-postgres>:5432/postgres
   SPRING_DATASOURCE_USERNAME=<usuario>
   SPRING_DATASOURCE_PASSWORD=<contraseña>
   APP_PUBLIC_URL=https://shotabra.midominio.com
   APP_ALLOWED_ORIGINS=https://shotabra.midominio.com
   ```
   Fíjate en que la URL JDBC empieza por `jdbc:postgresql://` (no `postgres://`) y no lleva el usuario ni la contraseña dentro.
4. **Dominio y puerto:** en *Domains* pon `https://shotabra.midominio.com` y en *Ports Exposes* pon `8080`. Coolify (Traefik) gestiona el HTTPS y el WebSocket sin configuración extra. La app ya respeta las cabeceras `X-Forwarded-*` (`server.forward-headers-strategy=framework`).
5. **Healthcheck:** la imagen trae un `HEALTHCHECK` contra `/actuator/health`. Si prefieres el de Coolify, activa *Healthcheck* con path `/actuator/health`, puerto `8080` y *start period* de unos 60 s.
6. **Deploy.** Al arrancar, Flyway crea las tablas y carga las palabras.

> **Una sola instancia:** el broker STOMP vive en memoria, así que no escales la app a varias réplicas. Para un juego de fiesta en casa, una instancia sobra.

---

## Cómo funciona

### Estructura

```
com.shotabra
├── config/      AppProperties (app.*), Clock/Random, scheduling
├── domain/      Party, Player, Word, RoundAssignment, Accusation (+ enums)
├── repository/  Spring Data JPA (bloqueo SELECT … FOR UPDATE de la party)
├── game/        GameService (toda la lógica), Derangements (Sattolo), WordPicker,
│                PartyCodeGenerator, PlayerNames, Emojis, errores, eventos y vistas
├── realtime/    WebSocketConfig, interceptores (identidad y permisos de suscripción),
│                PresenceTracker, GameNotifier (eventos → STOMP tras el commit)
├── jobs/        Traspaso de host (cada 10 s) y limpieza de parties inactivas (cada 15 min)
└── web/         PageController (Thymeleaf), GameApiController (REST), QrController, cookies
```

### API REST

Las acciones van por REST y devuelven el estado actualizado del jugador.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/`, `/j/{code}` | Página única. `/j/{code}` es el destino del QR. |
| `GET` | `/api/emojis` | Lista de emojis. |
| `GET` | `/api/parties/{code}` | Estado público de la party: si se puede entrar, nº de jugadores y emojis ocupados. |
| `POST` | `/api/parties` | Crear party (`{name, emoji}`). Devuelve `playerToken` y pone la cookie HttpOnly. |
| `POST` | `/api/parties/{code}/players` | Unirse a una party. |
| `GET` | `/api/me/state` | **Estado completo para reconectar:** lobby, tu tarjeta, acusación pendiente, resultado. |
| `POST` | `/api/game/start` · `/restart` · `/lobby` | Acciones del host. |
| `DELETE` | `/api/game/players/{id}` | El host expulsa a un jugador (solo en el lobby). |
| `POST` | `/api/game/leave` | Salir de la party (solo en el lobby). |
| `POST` | `/api/game/accusations` | «¡La dijo!». |
| `POST` | `/api/game/accusations/{id}/confirm` · `/reject` | Solo puede hacerlo el acusado. |
| `GET` | `/api/parties/{code}/qr.svg` | QR generado en el servidor. |

El jugador se identifica con la cookie `shotabra_player` (HttpOnly). Si se pierde, sirve la cabecera `X-Player-Token`, que el cliente guarda en `localStorage`.

### WebSocket (STOMP en `/ws`, con SockJS)

Todos los mensajes tienen la forma `{"type": "...", "data": {...}}`.

- **Público, `/topic/party/{code}`:** `PLAYER_JOINED`, `PLAYER_LEFT`, `PRESENCE_CHANGED`, `HOST_CHANGED`, `GAME_STARTED`, `ROUND_OVER` (con la revelación de todas las asignaciones) y `BACK_TO_LOBBY`.
- **Privado, `/user/queue/events`:** `ASSIGNMENT`, `ACCUSATION_RECEIVED`, `ACCUSATION_REJECTED` y `KICKED`.

El servidor solo deja suscribirse a la cola privada propia y al topic de la propia party. **La asignación de un jugador nunca se envía a otro**: ni en eventos públicos, ni en el HTML, ni en `/api/me/state`.

### Reconexión

El cliente se suscribe primero y **después** pide `GET /api/me/state`, así no pierde nada de lo que pasó mientras estaba fuera. Lo hace al cargar, al reconectar STOMP, al volver a la pestaña (`visibilitychange`) y al recuperar la red. Con eso, un móvil que se bloqueó o recargó vuelve a la pantalla exacta en la que estaba, con su tarjeta, el modal pendiente o el resultado.

### Concurrencia

Cada acción carga la party con `SELECT … FOR UPDATE`, así que las acciones sobre una misma party se ejecutan de una en una. Además, PostgreSQL garantiza con índices únicos parciales que haya **un solo `CONFIRMED` por ronda** y **un solo `PENDING` por acusador**. `@Version` en `party` queda como tercera red de seguridad.

---

## Licencia

Apache 2.0. Ver [LICENSE](LICENSE).
