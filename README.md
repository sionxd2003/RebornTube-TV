# RebornTube TV

Proyecto Android independiente para Fire TV, con tema Gruvbox oscuro. Al abrirlo, muestra un código de emparejamiento de seis cifras. En RebornTube para Android, ambos dispositivos deben poder comunicarse por la misma red local; usa **Transmitir a RebornTube para Fire TV** e introduce el código que aparece en la televisión. Si el descubrimiento automático no funciona, introduce en el móvil la dirección IP mostrada en esta pantalla.

Al abrir la app, un servicio en primer plano mantiene el receptor DIAL/SSDP disponible aunque salgas de la pantalla; Android muestra una notificación persistente de RebornTube TV. Mientras espera conexiones mantiene activos Wi-Fi y CPU para evitar que el reposo del Fire TV suspenda la escucha; esto puede aumentar el consumo eléctrico. Se inicia de nuevo después de reiniciar el Fire TV y reanuda la conexión si cambia la red. Vuelve a abrir la app desde esa notificación cuando quieras ver el código o el video recibido. Cerrar la notificación a la fuerza o forzar la detención desde Ajustes puede detener el receptor.

El receptor anuncia el servicio DIAL `RebornTube` y recibe `code`, `videoId`, `videoUrl`, `title` y `live` por HTTP dentro de la red local. El primer POST se autoriza con el código de seis cifras, que se reemplaza después de aceptar la solicitud. Cada POST aceptado responde con HTTP 200 y JSON `{"sessionToken":"..."}`; los siguientes pueden enviar ese token en lugar de `code`. El token aleatorio se guarda en el Fire TV y sobrevive a reinicios de la app o del servicio. Las solicitudes con un código o token inválido se rechazan. No uses este mecanismo en redes no confiables.

El reproductor usa controles RebornTube propios para el control remoto; se ocultan tras unos segundos de reproducción y reaparecen al usar el control. Incluye búsqueda, saltos de 10 segundos, velocidad, selección entre calidades disponibles, subtítulos cuando existen, repetición y compatibilidad con emisiones HLS. Se pueden volver a reproducir los últimos tres videos recibidos durante la sesión y reintentar una reproducción que haya fallado. En videos de YouTube combina las pistas DASH separadas de audio y video, igual que el reproductor Android. No ofrece transmitir a otra TV. La pantalla se mantiene despierta solo durante la reproducción. Los enlaces recibidos se resuelven con NewPipe Extractor; los enlaces directos HTTPS también se pueden reproducir.

## Compilar

Requisitos: Android Studio, JDK 17 y Android SDK Platform 35. Abre este directorio en Android Studio y ejecútalo en un dispositivo Fire TV o Android TV. Para generar el APK de depuración:

```powershell
.\gradlew.bat assembleDebug
```

En macOS/Linux:

```sh
bash ./gradlew assembleDebug
```

Para validar cambios localmente ejecuta `assembleDebug lintDebug testDebugUnitTest` con el wrapper correspondiente. GitHub Actions ejecuta estas tareas en cada push y pull request. El APK se genera en `app\build\outputs\apk\debug\app-debug.apk`. Si Gradle no encuentra el SDK de Android, configura `ANDROID_HOME` o el `sdk.dir` local.

El código de RebornTube TV se distribuye bajo GNU GPL versión 3 (GPL-3.0-only); consulta el archivo `LICENSE`. NewPipe Extractor también se distribuye bajo GPLv3. Revisa las licencias y obligaciones de las dependencias antes de distribuir la aplicación.
