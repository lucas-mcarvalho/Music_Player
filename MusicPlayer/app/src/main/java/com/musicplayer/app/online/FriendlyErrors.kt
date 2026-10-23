package com.musicplayer.app.online

/**
 * Traduz os erros do yt-dlp (em inglês) para mensagens no formato
 * "o que aconteceu + o que fazer", que é o que a pessoa precisa saber.
 */
object FriendlyErrors {
    fun of(raw: String?): String {
        val m = raw.orEmpty().lowercase()
        return when {
            "unable to resolve host" in m || "getaddrinfo" in m || "network is unreachable" in m ||
                "timed out" in m || "failed to establish" in m || "connection reset" in m ->
                "Sem conexão com a internet. Verifique o Wi-Fi ou os dados móveis e tente de novo."
            "not a bot" in m || "http error 429" in m || "too many requests" in m ->
                "O YouTube bloqueou downloads por alguns minutos. Espere um pouco e tente de novo."
            "confirm your age" in m || "age-restricted" in m || "inappropriate for some users" in m ->
                "Este vídeo tem restrição de idade e não pode ser baixado. Tente outro resultado."
            "private video" in m ->
                "Este vídeo é privado. Tente outro resultado."
            "video unavailable" in m || "this video is unavailable" in m || "removed" in m ->
                "Este vídeo não está mais disponível no YouTube. Tente outro resultado."
            "requested format is not available" in m || "no video formats" in m ->
                "Não há áudio disponível para este vídeo. Tente outro resultado."
            "http error 403" in m || "forbidden" in m ->
                "O YouTube recusou o download. Atualize o yt-dlp no menu ⋮ e tente de novo."
            "no space left" in m ->
                "Sem espaço no celular. Libere espaço e tente de novo."
            else ->
                "Não foi possível baixar. Tente de novo; se continuar, atualize o yt-dlp no menu ⋮."
        }
    }
}
