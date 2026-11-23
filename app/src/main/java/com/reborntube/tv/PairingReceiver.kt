package com.reborntube.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.LinkProperties
import android.os.PowerManager
import android.net.wifi.WifiManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class ReceivedVideo(
    val videoId: String,
    val videoUrl: String,
    val title: String,
    val live: Boolean,
    val startPositionMs: Long = 0L,
    val progressCallbackUrl: String = "",
    val progressToken: String = "",
    val fastVideoUrl: String = "",
    val fastAudioUrl: String = "",
    val fastHlsUrl: String = "",
    val fastQuality: String = "",
    val receivedAt: Long = System.nanoTime()
)

data class ReceiverStatus(
    val message: String,
    val video: ReceivedVideo? = null,
    val address: String? = null,
    val pairingCode: String? = null,
    val stopPlayback: Boolean = false
)

class PairingReceiver(
    context: Context,
    pairingCode: String,
    private val onStatusChanged: (ReceiverStatus) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    private val pairingLock = Any()
    private val workers: ExecutorService = Executors.newFixedThreadPool(4)
    @Volatile private var activePairingCode = pairingCode
    @Volatile private var httpServer: ServerSocket? = null
    @Volatile private var ssdpSocket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var cpuLock: PowerManager.WakeLock? = null
    @Volatile private var network: Network? = null
    @Volatile private var networkInterface: NetworkInterface? = null
    @Volatile private var address = "127.0.0.1"

    fun start() {
        if (!running.compareAndSet(false, true)) return
        try {
            val selectedNetwork = localNetwork()
            network = selectedNetwork.network
            networkInterface = selectedNetwork.networkInterface
            address = selectedNetwork.address
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("reborntube-tv-ssdp").apply {
                setReferenceCounted(false)
                acquire()
            }
            wifiLock = wifi.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "reborntube-tv-wifi"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            cpuLock = power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "RebornTubeTV:pairing"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            httpServer = ServerSocket().apply {
                bind(java.net.InetSocketAddress(InetAddress.getByName(address), 8765))
            }
            workers.execute(::serveHttp)
            workers.execute(::serveSsdp)
            publish(ReceiverStatus("Listo para emparejar · misma red Wi-Fi", address = address))
        } catch (error: Exception) {
            stopListening()
            publish(ReceiverStatus("No se pudo iniciar el receptor: ${error.message ?: "error de red"}"))
        }
    }

    fun stop() {
        stopListening()
        workers.shutdownNow()
    }

    fun restart() {
        stopListening()
        start()
    }

    private fun stopListening() {
        running.set(false)
        runCatching { httpServer?.close() }
        runCatching { ssdpSocket?.close() }
        httpServer = null
        ssdpSocket = null
        network = null
        networkInterface = null
        runCatching { if (cpuLock?.isHeld == true) cpuLock?.release() }
        cpuLock = null
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        wifiLock = null
        runCatching { if (multicastLock?.isHeld == true) multicastLock?.release() }
        multicastLock = null
    }

    private fun serveHttp() {
        while (running.get()) {
            try {
                val client = httpServer?.accept() ?: break
                workers.execute { handleHttp(client) }
            } catch (_: Exception) {
                if (running.get()) publish(ReceiverStatus("Se perdió la conexión con la red local"))
            }
        }
    }

    private fun handleHttp(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 5000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            try {
                val requestLine = readLine(input, 4096) ?: return
                val parts = requestLine.split(' ', limit = 3)
                if (parts.size < 2) {
                    respond(output, 400, "Bad Request")
                    return
                }
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input, 8192) ?: break
                    if (line.isEmpty()) break
                    val split = line.indexOf(':')
                    if (split > 0) headers[line.substring(0, split).trim().lowercase()] =
                        line.substring(split + 1).trim()
                }
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                if (length !in 0..65536) {
                    respond(output, 413, "Payload Too Large")
                    return
                }
                val body = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val count = input.read(body, offset, length - offset)
                    if (count < 0) break
                    offset += count
                }

                when {
                    parts[0] == "GET" && parts[1] == "/ssdp/device-desc.xml" ->
                        respond(
                            output,
                            200,
                            deviceDescription(),
                            "text/xml; charset=utf-8",
                            mapOf("Application-URL" to "http://$address:8765/apps/")
                        )
                    parts[0] == "GET" && parts[1].startsWith("/apps/RebornTube") ->
                        respond(output, 200, appStatus(), "text/xml; charset=utf-8")
                    parts[0] == "POST" && parts[1].startsWith("/apps/RebornTube") ->
                        acceptPairing(body.toString(Charsets.UTF_8), output)
                    else -> respond(output, 404, "Not Found")
                }
            } catch (_: SocketTimeoutException) {
                respond(output, 408, "Request Timeout")
            } catch (error: Exception) {
                publish(ReceiverStatus("No se pudo procesar la solicitud: ${error.message ?: "error"}"))
                runCatching { respond(output, 400, "Bad Request") }
            }
        }
    }

    private fun acceptPairing(body: String, output: BufferedOutputStream) {
        synchronized(pairingLock) {
            val fields = body.split('&').mapNotNull { item ->
                val index = item.indexOf('=')
                if (index < 0) null else URLDecoder.decode(item.substring(0, index), "UTF-8") to
                    URLDecoder.decode(item.substring(index + 1), "UTF-8")
            }.toMap()
            val receivedCode = fields["code"].orEmpty()
            val suppliedToken = fields["sessionToken"].orEmpty()
            val authorizedByToken = suppliedToken.isNotEmpty()
            val authorized = if (authorizedByToken) {
                getSessionToken(appContext)?.let { expectedToken ->
                    MessageDigest.isEqual(
                        expectedToken.toByteArray(Charsets.UTF_8),
                        suppliedToken.toByteArray(Charsets.UTF_8)
                    )
                } ?: false
            } else {
                receivedCode.length == 6 && receivedCode.all(Char::isDigit) &&
                    MessageDigest.isEqual(
                        activePairingCode.toByteArray(Charsets.UTF_8),
                        receivedCode.toByteArray(Charsets.UTF_8)
                    )
            }
            if (!authorized) {
                val message = if (authorizedByToken) "Token de sesión inválido."
                    else "Código incorrecto · escribe en el celular las 6 cifras que muestra esta pantalla."
                publish(ReceiverStatus(message))
                respond(output, 403, "Invalid pairing code or session token")
                return
            }
            if (fields["action"] == "stop") {
                if (!authorizedByToken) {
                    respond(output, 403, "A session token is required to stop playback")
                    return
                }
                publish(
                    ReceiverStatus(
                        "Desconectado del celular · listo para emparejar",
                        address = address,
                        pairingCode = activePairingCode,
                        stopPlayback = true
                    )
                )
                respond(
                    output,
                    200,
                    """{"sessionToken":"${getOrCreateSessionToken(appContext)}"}""",
                    "application/json; charset=utf-8"
                )
                return
            }
            val videoUrl = fields["videoUrl"].orEmpty()
            val videoId = fields["videoId"].orEmpty()
            if (videoUrl.isNotBlank() && !videoUrl.startsWith("https://")) {
                respond(output, 400, "Only HTTPS video URLs are accepted")
                return
            }
            val video = if (videoUrl.isNotBlank() || videoId.isNotBlank()) {
                val live = fields["live"].equals("true", true)
                val callbackUrl = fields["progressCallbackUrl"].orEmpty()
                    .takeIf(::isValidProgressCallback).orEmpty()
                val progressToken = fields["progressToken"].orEmpty()
                    .takeIf { it.matches(Regex("[A-Za-z0-9_-]{32,64}")) }.orEmpty()
                ReceivedVideo(
                    videoId = videoId,
                    videoUrl = videoUrl,
                    title = fields["title"].orEmpty().ifBlank { "Reproduciendo en RebornTube" },
                    live = live,
                    startPositionMs = if (live) 0L else fields["positionMs"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0L,
                    progressCallbackUrl = callbackUrl,
                    progressToken = progressToken,
                    fastVideoUrl = fields["fastVideoUrl"].orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
                    fastAudioUrl = fields["fastAudioUrl"].orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
                    fastHlsUrl = fields["fastHlsUrl"].orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
                    fastQuality = fields["fastQuality"].orEmpty().take(16)
                )
            } else null
            if (!authorizedByToken) {
                activePairingCode = newCode(receivedCode)
                saveCode(appContext, activePairingCode)
            }
            val sessionToken = getOrCreateSessionToken(appContext)
            publish(ReceiverStatus(
                if (video == null) "¡Emparejado! Abre un video en tu celular y envíalo a este Fire TV."
                else "Conectado · ${video.title}",
                video
            ))
            respond(
                output,
                200,
                """{"sessionToken":"$sessionToken"}""",
                "application/json; charset=utf-8"
            )
        }
    }

    private fun serveSsdp() {
        var socket: MulticastSocket? = null
        try {
            val nic = networkInterface ?: error("No se encontró la interfaz de red del Fire TV")
            socket = MulticastSocket(null).apply {
                reuseAddress = true
                soTimeout = 1000
                network?.bindSocket(this)
                bind(java.net.InetSocketAddress(1900))
                joinGroup(
                    java.net.InetSocketAddress(InetAddress.getByName("239.255.255.250"), 1900),
                    nic
                )
            }

            ssdpSocket = socket
            val buffer = ByteArray(4096)
            while (running.get()) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                if (!message.startsWith("M-SEARCH", true) ||
                    !message.contains("urn:dial-multiscreen-org:service:dial:1", true) &&
                    !message.contains("ssdp:all", true)
                ) continue
                val target = message.lineSequence().firstOrNull { it.startsWith("ST:", true) }
                    ?.substringAfter(':')?.trim() ?: "urn:dial-multiscreen-org:service:dial:1"
                val response = (
                    "HTTP/1.1 200 OK\r\n" +
                        "CACHE-CONTROL: max-age=1800\r\n" +
                        "EXT:\r\n" +
                        "LOCATION: http://$address:8765/ssdp/device-desc.xml\r\n" +
                        "SERVER: Linux/3.0 UPnP/1.0 RebornTubeTV/1.0\r\n" +
                        "ST: $target\r\n" +
                        "USN: uuid:reborntube-tv-$address::$target\r\n" +
                        "Application-URL: http://$address:8765/apps/\r\n\r\n"
                    ).toByteArray(Charsets.UTF_8)
                socket.send(DatagramPacket(response, response.size, packet.address, packet.port))
            }
        } catch (error: Exception) {
            if (running.get()) publish(
                ReceiverStatus("Receptor de red limitado: ${error.message ?: "SSDP no disponible"}")
            )
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun isValidProgressCallback(value: String): Boolean = runCatching {
        val uri = URI(value)
        val octets = uri.host?.split('.')?.map { it.toIntOrNull() ?: -1 }.orEmpty()
        val privateAddress = octets.size == 4 && octets.all { it in 0..255 } && (
            octets[0] == 10 ||
                octets[0] == 192 && octets[1] == 168 ||
                octets[0] == 172 && octets[1] in 16..31
            )
        uri.scheme == "http" && uri.port == 8766 && uri.path == "/progress" && privateAddress
    }.getOrDefault(false)

    private fun deviceDescription() = """
        <?xml version="1.0" encoding="UTF-8"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dial="urn:schemas-upnp-org:device-1-0">
          <specVersion><major>1</major><minor>0</minor></specVersion>
          <device>
            <deviceType>urn:dial-multiscreen-org:device:dial:1</deviceType>
            <friendlyName>RebornTube Fire TV</friendlyName>
            <manufacturer>Amazon</manufacturer>
            <modelName>AFT RebornTube</modelName>
            <UDN>uuid:reborntube-tv-$address</UDN>
            <serviceList>
              <service>
                <serviceType>urn:dial-multiscreen-org:service:dial:1</serviceType>
                <serviceId>urn:dial-multiscreen-org:serviceId:dial</serviceId>
                <controlURL>/apps</controlURL>
                <eventSubURL>/apps</eventSubURL>
                <SCPDURL>/dial.xml</SCPDURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    private fun appStatus() =
        """<?xml version="1.0" encoding="UTF-8"?><service xmlns="urn:dial-multiscreen-org:schemas:dial"><name>RebornTube</name><options allowStop="true"/><state>running</state></service>"""

    private fun respond(
        output: BufferedOutputStream,
        code: Int,
        body: String,
        contentType: String = "text/plain; charset=utf-8",
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            408 -> "Request Timeout"
            413 -> "Payload Too Large"
            else -> "Error"
        }
        val headers = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            extraHeaders.forEach { (name, value) -> append("$name: $value\r\n") }
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private fun publish(status: ReceiverStatus) {
        val updatedStatus = status.copy(
            address = status.address ?: address,
            pairingCode = activePairingCode
        )
        PairingState.update(updatedStatus)
        onStatusChanged(updatedStatus)
    }

    private fun readLine(input: BufferedInputStream, limit: Int): String? {
        val bytes = ArrayList<Byte>()
        while (bytes.size < limit) {
            val next = input.read()
            if (next < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(Charsets.US_ASCII)
            if (next == '\n'.code) break
            if (next != '\r'.code) bytes.add(next.toByte())
        }
        if (bytes.size >= limit) error("HTTP header line too long")
        return bytes.toByteArray().toString(Charsets.US_ASCII)
    }

    private data class NetworkAddress(
        val network: Network?,
        val address: String,
        val networkInterface: NetworkInterface
    )

    private fun localNetwork(): NetworkAddress {
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = connectivity.activeNetwork
        val candidates = connectivity.allNetworks
            .mapNotNull { androidNetwork ->
                val capabilities = connectivity.getNetworkCapabilities(androidNetwork) ?: return@mapNotNull null
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                ) return@mapNotNull null
                val properties: LinkProperties = connectivity.getLinkProperties(androidNetwork) ?: return@mapNotNull null
                val interfaceName = properties.interfaceName ?: return@mapNotNull null
                val nic = NetworkInterface.getByName(interfaceName) ?: return@mapNotNull null
                if (!nic.isUp || nic.isLoopback) return@mapNotNull null
                val ipv4 = properties.linkAddresses
                    .map { it.address }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
                    ?: return@mapNotNull null
                Triple(androidNetwork, nic, ipv4)
            }
            .sortedByDescending { it.first == active }
        val selected = candidates.firstOrNull()
            ?: error("Conecta el Fire TV a la misma red Wi-Fi que el celular")
        return NetworkAddress(
            selected.first,
            selected.third.hostAddress ?: error("No se pudo leer la dirección de red del Fire TV"),
            selected.second
        )
    }

    companion object {
        fun getOrCreateCode(context: Context): String {
            val preferences = context.getSharedPreferences("fire_tv_pairing", Context.MODE_PRIVATE)
            val existing = preferences.getString("code", null)
            if (existing != null && existing.matches(Regex("\\d{6}"))) return existing
            return newCode(existing).also { saveCode(context, it) }
        }

        fun saveCode(context: Context, code: String) {
            require(code.matches(Regex("\\d{6}")))
            context.getSharedPreferences("fire_tv_pairing", Context.MODE_PRIVATE)
                .edit().putString("code", code).apply()
        }

        fun newCode(previousCode: String? = null): String {
            val random = SecureRandom()
            var code: String
            do {
                code = random.nextInt(1_000_000).toString().padStart(6, '0')
            } while (code == previousCode)
            return code
        }

        fun getOrCreateSessionToken(context: Context): String {
            getSessionToken(context)?.let { return it }

            val preferences = context.getSharedPreferences("fire_tv_pairing", Context.MODE_PRIVATE)
            val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
            val token = android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
            preferences.edit().putString("session_token", token).apply()
            return token
        }

        private fun getSessionToken(context: Context): String? {
            val token = context.getSharedPreferences("fire_tv_pairing", Context.MODE_PRIVATE)
                .getString("session_token", null)
            return token?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{43}")) }
        }
    }
}
