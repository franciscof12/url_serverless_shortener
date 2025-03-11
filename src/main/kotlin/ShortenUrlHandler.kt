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
        return try {
            val body = input["body"] as? String ?: return apiGatewayError("Missing body")

            val jsonBody: Map<String, String> = try {
                objectMapper.readValue(body, Map::class.java) as Map<String, String>
            } catch (e: Exception) {
                return apiGatewayError("JSON Parsing Error: ${e.message}")
            }

            val longUrl = jsonBody["long_url"] ?: return apiGatewayError("Missing 'long_url' field")

            val shortId = getRandomUUID()

            val item = mapOf(
                "short_id" to AttributeValue(shortId),
                "long_url" to AttributeValue(longUrl)
            )

            saveItemOnDynamoDB(item)

            val shortUrl = System.getenv("AWS_GATEWAY_BASE_URL") + shortId
            return apiGatewaySuccess(mapOf("short_url" to shortUrl))

        } catch (e: Exception) {
            apiGatewayError("Unexpected Error: ${e.message}")
        }
    }

    private fun getRandomUUID(): String {
        return UUID.randomUUID().toString().substring(0, 6)
    }

    private fun saveItemOnDynamoDB(item: Map<String, AttributeValue>) {
        dynamoDB.putItem(PutItemRequest().apply {
            tableName = "url_shortener"
            this.item = item
        })
    }

    private fun apiGatewaySuccess(body: Map<String, String>): Map<String, Any> {
        return mapOf(
            "statusCode" to 200,
            "headers" to mapOf("Content-Type" to "application/json"),
            "body" to objectMapper.writeValueAsString(body)
        )
    }

    private fun apiGatewayError(message: String): Map<String, Any> {
        return mapOf(
            "statusCode" to 400,
            "headers" to mapOf("Content-Type" to "application/json"),
            "body" to objectMapper.writeValueAsString(mapOf("error" to message))
        )
    }
}