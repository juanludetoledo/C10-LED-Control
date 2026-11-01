C10 LED Control - proyecto Android inicial

Proyecto creado para el firmware VITOR2_WIFI_BONITA_ON_OFF(1).ino.
Usa:
- Nombre BLE: C10_LED_CONTROL
- Servicio: 5b8f0001-7a2d-4c61-9b21-7c10c10a0001
- Escritura: 5b8f0002-7a2d-4c61-9b21-7c10c10a0001
- Estado: 5b8f0003-7a2d-4c61-9b21-7c10c10a0001
- Comandos: ON y OFF

Cómo generar el APK:
1. Instala Android Studio en un ordenador.
2. Abre la carpeta C10_LED_Control_Android (File > Open).
3. Acepta la sincronización de Gradle e instala las plataformas SDK que solicite.
4. Conecta el móvil o selecciona Build > Build Bundle(s) / APK(s) > Build APK(s).
5. El APK de depuración aparecerá normalmente en app/build/outputs/apk/debug/app-debug.apk.

Notas:
- Este es el proyecto fuente; no es un APK compilado.
- En el Samsung, permite el acceso a dispositivos cercanos/Bluetooth cuando lo solicite.
- Activa Bluetooth, enciende el ESP32 y pulsa CONECTAR A LA BARRA.
- Prueba los botones con el vehículo estacionado. El botón solo controla la barra a través del firmware instalado.


COMPILACIÓN AUTOMÁTICA EN LA NUBE (sin instalar Android Studio):
El proyecto incluye .github/workflows/build-apk.yml para GitHub Actions.
Para usarlo hay que subir el contenido del ZIP a un repositorio GitHub, abrir la pestaña Actions, ejecutar "Build C10 LED Control APK" y descargar el artefacto "C10-LED-Control-APK". Esto requiere una cuenta GitHub y conexión a internet.
