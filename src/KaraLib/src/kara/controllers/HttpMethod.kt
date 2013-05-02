package kara

public enum class HttpMethod {
    GET
    POST
    PUT
    DELETE
}

fun String.asHttpMethod(): HttpMethod = when (this) {
    "GET" -> HttpMethod.GET
    "POST" -> HttpMethod.POST
    "PUT" -> HttpMethod.PUT
    "DELETE" -> HttpMethod.DELETE
    else -> throw RuntimeException("Unknown $this as HTTP method")
}
