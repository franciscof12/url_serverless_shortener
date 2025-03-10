import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.GetItemRequest
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.ObjectMapper

class RedirectUrlHandler : RequestHandler<Map<String, Any>, Map<String, Any>> {

    private val dynamoDB = AmazonDynamoDBClientBuilder.defaultClient()
    private val objectMapper = ObjectMapper()

    override fun handleRequest(input: Map<String, Any>, context: Context): Map<String, Any> {
        context.logger.log("🚀 [Lambda Start] Received input: $input")

        val pathParams = input["pathParameters"] as? Map<String, String>
        val shortId = pathParams?.get("shortId") ?: return apiGatewayError(400, "Missing short_id", context)

        context.logger.log("🔍 Looking up shortId: $shortId")

        val originalUrl = getOriginalUrlFromDynamoDB(shortId)
            ?: return apiGatewayError(404, "URL not found", context)

        context.logger.log("✅ Found original URL: $originalUrl")

        return apiGatewayRedirect(originalUrl)
    }

    private fun getOriginalUrlFromDynamoDB(shortId: String): String? {
        return try {
            val request = GetItemRequest().apply {
                tableName = "url_shortener"
                key = mapOf("short_id" to AttributeValue(shortId))
            }

            val item = dynamoDB.getItem(request).item ?: return null
            item["long_url"]?.s
        } catch (e: Exception) {
            null
        }
    }

    private fun apiGatewayRedirect(url: String): Map<String, Any> {
        return mapOf(
            "statusCode" to 302,
            "headers" to mapOf("Location" to url),
            "body" to ""
        )
    }

    private fun apiGatewayError(statusCode: Int, message: String, context: Context): Map<String, Any> {
        context.logger.log("❌ ERROR ($statusCode): $message")
        return mapOf(
            "statusCode" to statusCode,
            "headers" to mapOf("Content-Type" to "application/json"),
            "body" to objectMapper.writeValueAsString(mapOf("error" to message))
        )
    }
}