import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.PutItemRequest
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URL
import java.util.UUID

class ShortenUrlHandler : RequestHandler<Map<String, Any>, Map<String, String>> {

    private val dynamoDB = AmazonDynamoDBClientBuilder.defaultClient()
    private val objectMapper = ObjectMapper() // ObjectMapper para parsear el JSON de entrada

    override fun handleRequest(input: Map<String, Any>, context: Context): Map<String, String> {
        context.logger.log("Received input: $input")

        val body = input["body"] as? String ?: return mapOf("error" to "Missing body")

        val jsonBody: Map<String, String> = try {
            objectMapper.readValue(body, Map::class.java) as Map<String, String>
        } catch (e: Exception) {
            return mapOf("error" to "Invalid JSON format: ${e.message}")
        }

        val longUrl = jsonBody["long_url"] ?: return mapOf("error" to "Missing 'long_url' field")

        if (!isValidUrl(longUrl)) {
            return mapOf("error" to "Invalid URL format")
        }

        val shortId = getRandomUUID()

        val item = mapOf(
            "short_id" to AttributeValue(shortId),
            "long_url" to AttributeValue(longUrl)
        )

        try {
            saveItemOnDynamoDB(item)
        } catch (e: Exception) {
            return mapOf("error" to "Error saving to database: ${e.message}")
        }

        val shortUrl = System.getenv("AWS_GATEWAY_BASE_URL") + shortId

        return mapOf("short_url" to shortUrl)
    }

    private fun getRandomUUID(): String {
        return UUID.randomUUID().toString().substring(0, 6)
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            URL(url).toURI()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun saveItemOnDynamoDB(item: Map<String, AttributeValue>) {
        dynamoDB.putItem(PutItemRequest().apply {
            tableName = "url_shortener"
            this.item = item
        })
    }
}