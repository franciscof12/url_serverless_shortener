import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.PutItemRequest
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID

class ShortenUrlHandler : RequestHandler<Map<String, Any>, Map<String, Any>> {

    private val dynamoDB = AmazonDynamoDBClientBuilder.defaultClient()
    private val objectMapper = ObjectMapper()

    override fun handleRequest(input: Map<String, Any>, context: Context): Map<String, Any> {
        context.logger.log("🚀 [Lambda Start] Received input: $input")

        return try {
            val body = input["body"] as? String ?: return apiGatewayError("Missing body", context)

            val jsonBody: Map<String, String> = try {
                objectMapper.readValue(body, Map::class.java) as Map<String, String>
            } catch (e: Exception) {
                return apiGatewayError("JSON Parsing Error: ${e.message}", context)
            }

            val longUrl = jsonBody["long_url"] ?: return apiGatewayError("Missing 'long_url' field", context)
            context.logger.log("✅ Received long_url: $longUrl")

            if (!isValidUrl(longUrl)) {
                return apiGatewayError("Invalid URL format", context)
            }

            val shortId = getRandomUUID()
            context.logger.log("🔗 Generated short ID: $shortId")

            val item = mapOf(
                "short_id" to AttributeValue(shortId),
                "long_url" to AttributeValue(longUrl)
            )

            try {
                saveItemOnDynamoDB(item, context)
            } catch (e: Exception) {
                return apiGatewayError("DynamoDB Error: ${e.message}", context)
            }

            val shortUrl = System.getenv("AWS_GATEWAY_BASE_URL") + shortId
            context.logger.log("✅ Short URL created: $shortUrl")

            return apiGatewaySuccess(mapOf("short_url" to shortUrl))

        } catch (e: Exception) {
            apiGatewayError("Unexpected Error: ${e.message}", context)
        }
    }

    private fun getRandomUUID(): String {
        return UUID.randomUUID().toString().substring(0, 6)
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun saveItemOnDynamoDB(item: Map<String, AttributeValue>, context: Context) {
        try {
            context.logger.log("🛠️ Saving item to DynamoDB: $item")
            dynamoDB.putItem(PutItemRequest().apply {
                tableName = "url_shortener"
                this.item = item
            })
            context.logger.log("✅ DynamoDB insert successful for: $item")
        } catch (e: Exception) {
            context.logger.log("❌ DynamoDB Exception: ${e.message}")
            throw e
        }
    }

    private fun apiGatewaySuccess(body: Map<String, String>): Map<String, Any> {
        return mapOf(
            "statusCode" to 200,
            "headers" to mapOf("Content-Type" to "application/json"),
            "body" to objectMapper.writeValueAsString(body)
        )
    }

    private fun apiGatewayError(message: String, context: Context): Map<String, Any> {
        context.logger.log("❌ ERROR: $message")
        return mapOf(
            "statusCode" to 400,
            "headers" to mapOf("Content-Type" to "application/json"),
            "body" to objectMapper.writeValueAsString(mapOf("error" to message))
        )
    }
}