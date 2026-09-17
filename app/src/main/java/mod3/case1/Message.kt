package mod3.case1

data class Message(
    val text: String,
    val isUser: Boolean
)

data class QueryRequest(val question: String)

data class QueryResponse(val answer: String)

data class UploadResponse(
    val message: String,
    val chunks: Int? = null
)