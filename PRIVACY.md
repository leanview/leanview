# Privacy / Privacidad

[Español](#español) | [English](#english)

---

## Español

**Leanview no recoge nada.** No hay cuenta de Leanview, ni análisis de uso, ni informes de errores, ni publicidad, ni telemetría, y no existe ningún servidor de Leanview que reciba datos. La aplicación no incluye Firebase, Crashlytics ni ningún componente parecido (se comprobó en el código y en el APK publicado). Tus ajustes, tu historial local y los datos de inicio de sesión se guardan solo en tu dispositivo.

Lo que sí ocurre es que la aplicación se conecta directamente desde tu dispositivo a los servicios que necesita. Esta es la lista completa, con lo que ve cada uno (siempre, como mínimo, tu dirección IP).

### Siempre, para poder reproducir

- **YouTube y Google** (youtube.com, googlevideo.com, googleapis.com, ytimg.com, ggpht.com, google.com): lo que buscas y miras, y, si inicias sesión, las acciones de tu cuenta (suscripciones, "me gusta", historial). Es lo mismo que enviaría cualquier aplicación de YouTube; rige la política de privacidad de Google.
- **Sugerencias de búsqueda** (clients1.google.com): el texto que vas escribiendo en el buscador.

### Servicios de la comunidad (el identificador del vídeo, nada más)

- **SponsorBlock** (sponsor.ajay.app): activado por defecto. Recibe el identificador del vídeo que reproduces para devolver los tramos que se pueden saltar. Se desactiva en los ajustes de SponsorBlock.
- **Return YouTube Dislike** (returnyoutubedislikeapi.com): activado por defecto. Recibe el identificador del vídeo para mostrar el contador de "no me gusta". Se desactiva con la opción "Mostrar contador de me gusta/no me gusta" de los ajustes del reproductor.
- **DeArrow** (sponsor.ajay.app, dearrow-thumb.ajay.app): desactivado por defecto. Solo si lo activas, recibe los identificadores de los vídeos que ves en pantalla.

### Casos puntuales

- **Códigos QR**: los códigos QR de la aplicación (inicio de sesión, compartir por QR, enlaces de Acerca de) se dibujan en tu dispositivo. No se piden a ningún servicio de QR, así que ni el código de inicio de sesión ni el enlace que compartes salen del dispositivo por ese motivo.
- **via.placeholder.com** puede recibir una petición de una imagen negra de relleno cuando un vídeo no se puede reproducir y no tiene imagen de fondo.
- **flagcdn.com** recibe el código de tu país solo si activas el botón de idioma de la barra superior (no está activado por defecto).
- **DNS público de Google (8.8.8.8)**: si lo eliges en los ajustes de red, o si la aplicación cambia a él después de que un vídeo se quede parado, los nombres de los servidores a los que se conecta la aplicación se consultan allí.
- **Micrófono**: el permiso se usa solo para la búsqueda por voz, que realiza el servicio de reconocimiento de voz de Android. Ese servicio, que no es de Leanview, puede enviar el audio a su proveedor (normalmente Google); lo gobiernan los ajustes de voz de tu dispositivo. Leanview no graba ni guarda audio.
- **Respaldo del bloqueo de desafíos de YouTube**: para reproducir, Leanview resuelve un desafío de YouTube con unos scripts que ya vienen incluidos en la aplicación. Solo si fallaran los incluidos y los guardados, descargaría la copia publicada de esos scripts desde github.com (proyecto yt-dlp/ejs). Es poco habitual.

### Actualizaciones (github.com)

Al abrirse, y como mucho una vez por minuto, Leanview descarga `https://github.com/leanview/leanview/releases/latest/download/update.json` para saber si hay una versión nueva. Si la hay, **descarga el APK en segundo plano** desde la misma página de versiones (GitHub y sus servidores de descarga) y después te pide confirmar la instalación. GitHub ve tu dirección IP y la hora; Leanview no envía ninguna otra información. Puedes desactivar la comprobación en Ajustes > Acerca de.

### El archivo constants.json de SmartTube (github.com/yuliskov/SmartTube)

Leanview se basa en SmartTube y, para el inicio de sesión con Google y la copia de seguridad en Google Drive, necesita un identificador de cliente OAuth de Google que SmartTube publica en un archivo. Por eso, la primera vez que algo lo necesita en una sesión de la aplicación (inicio de sesión con Google, renovación de la sesión, copia de seguridad en Drive), Leanview descarga `https://github.com/yuliskov/SmartTube/releases/download/latest/constants.json` desde GitHub. No lo hace al arrancar ni al reproducir vídeos.

- Qué se pide: ese archivo, con una petición `GET` normal. La petición no lleva ninguna cuenta, cookie, vídeo ni búsqueda.
- Qué ve GitHub: tu dirección IP, la hora y que una aplicación pidió ese archivo de SmartTube.
- Qué pasa si falta o GitHub no responde: el inicio de sesión con Google falla (y con él la copia de seguridad en Drive). La reproducción normal no lo usa.
- El archivo también trae una clave de la API de datos de YouTube; Leanview no la usa. Los nombres e iconos de los canales (grupos de canales, importación de suscripciones) salen de las mismas peticiones a YouTube que la aplicación hace para la página de un canal.
- La pantalla de consentimiento de Google puede mostrar el nombre del proyecto de SmartTube, porque el identificador de cliente pertenece a su proyecto de Google Cloud. Leanview es independiente de SmartTube y de Google.

### Tu propio servidor y tu instancia de Invidious (opcionales)

Si escribes la dirección de un servidor compatible o de una instancia de Invidious, tus datos van solo ahí, y a ninguna otra parte: la dirección se guarda en tu dispositivo y Leanview no trae ninguna incorporada. Ese servidor ve tu dirección IP, tus cambios de suscripciones y la posición de tus vídeos (hora, progreso y duración, con los identificadores en forma de huella); el título, el autor, la miniatura y los identificadores reales del vídeo y del canal viajan cifrados con una clave que solo está en tus dispositivos. Lo que ese servidor hace con ello depende de quien lo administra. Sin servidor ni instancia, Leanview no hace ninguna petición a ellos.

### Inicio de sesión con Google y copia de seguridad

Si inicias sesión con Google, la autorización se hace con el flujo oficial de Google; las credenciales de acceso se guardan en tu dispositivo, no en Leanview. Si guardas una copia de seguridad en Google Drive, va a tu propio Drive. Las copias locales se guardan en la carpeta `LeanviewBackup` de Documentos.

### Cambios

Si esta política cambia, se publica aquí y se anota en el [CHANGELOG](CHANGELOG.md).

---

## English

**Leanview collects nothing.** There is no Leanview account, no usage analytics, no crash reports, no advertising and no telemetry, and there is no Leanview server that receives data. The app contains no Firebase, Crashlytics or similar component (checked in the code and in the published APK). Your settings, local history and sign-in data stay on your device only.

What does happen is that the app connects directly from your device to the services it needs. This is the complete list, with what each one sees (always, at least, your IP address).

### Always, to play videos

- **YouTube and Google** (youtube.com, googlevideo.com, googleapis.com, ytimg.com, ggpht.com, google.com): what you search for and watch, and, if you sign in, your account actions (subscriptions, likes, history). It is what any YouTube app would send; Google's privacy policy applies.
- **Search suggestions** (clients1.google.com): the text you type in the search box.

### Community services (the video identifier, nothing else)

- **SponsorBlock** (sponsor.ajay.app): on by default. It receives the identifier of the video you play, to return the segments that can be skipped. Turn it off in the SponsorBlock settings.
- **Return YouTube Dislike** (returnyoutubedislikeapi.com): on by default. It receives the video identifier to show the dislike counter. Turn it off with the "Show likes/dislikes count" option in the player settings.
- **DeArrow** (sponsor.ajay.app, dearrow-thumb.ajay.app): off by default. Only if you turn it on, it receives the identifiers of the videos shown on screen.

### Occasional cases

- **QR codes**: the QR codes of the app (sign-in, share by QR, the About links) are drawn on your device. They are not requested from any QR service, so neither the sign-in code nor the link you share leaves the device for that.
- **via.placeholder.com** may receive a request for a plain black filler image when a video cannot be played and has no background image.
- **flagcdn.com** receives your country code only if you turn on the language button in the top bar (it is not on by default).
- **Google Public DNS (8.8.8.8)**: if you choose it in the network settings, or the app switches to it after a playback stall, the names of the servers the app connects to are looked up there.
- **Microphone**: the permission is used only for voice search, which is done by Android's speech recognition service. That service, which is not part of Leanview, may send the audio to its provider (usually Google); your device's voice settings govern it. Leanview does not record or store audio.
- **Fallback for YouTube's challenge**: to play videos, Leanview solves a YouTube challenge with scripts that are already bundled in the app. Only if the bundled and the cached ones failed would it download the published copy of those scripts from github.com (the yt-dlp/ejs project). This is unusual.

### Updates (github.com)

When it opens, and at most once a minute, Leanview downloads `https://github.com/leanview/leanview/releases/latest/download/update.json` to learn whether a newer version exists. If there is one, it **downloads the APK in the background** from the same releases page (GitHub and its download servers) and then asks you to confirm the install. GitHub sees your IP address and the time; Leanview sends no other information. You can turn the check off in Settings > About.

### SmartTube's constants.json file (github.com/yuliskov/SmartTube)

Leanview is based on SmartTube and, for Google sign-in and Google Drive backup, it needs a Google OAuth client identifier that SmartTube publishes in a file. So the first time something needs it in a run of the app (Google sign-in, refreshing the session, Drive backup), Leanview downloads `https://github.com/yuliskov/SmartTube/releases/download/latest/constants.json` from GitHub. It does not do this at startup or while playing videos.

- What is requested: that file, with a plain `GET` request. The request carries no account, cookie, video or search.
- What GitHub sees: your IP address, the time, and that an app asked for that SmartTube file.
- What happens if it is missing or GitHub does not answer: Google sign-in fails (and with it Drive backup). Normal playback does not use it.
- The file also carries a YouTube Data API key; Leanview does not use it. Channel names and icons (channel groups, subscription import) come from the same YouTube requests the app makes for a channel's page.
- Google's consent screen may show the name of SmartTube's project, because the client identifier belongs to its Google Cloud project. Leanview is independent of SmartTube and Google.

### Your own server and Invidious instance (optional)

If you type the address of a compatible server or an Invidious instance, your data goes only there, and nowhere else: the address is stored on your device and Leanview has none built in. That server sees your IP address, your subscription changes and the position of your videos (time, progress and duration, with identifiers in fingerprint form); the title, author, thumbnail and the real video and channel identifiers travel encrypted with a key that exists only on your devices. What that server does with it depends on whoever runs it. Without a server and an instance, Leanview makes no request to them.

### Google sign-in and backup

If you sign in with Google, authorization uses Google's official flow; the access credentials are kept on your device, not with Leanview. If you save a backup to Google Drive it goes to your own Drive. Local backups are saved in the `LeanviewBackup` folder of Documents.

### Changes

If this policy changes it is published here and noted in the [CHANGELOG](CHANGELOG.md).
