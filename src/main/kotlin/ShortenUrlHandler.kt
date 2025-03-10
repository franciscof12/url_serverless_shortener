import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.PutItemRequest
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import java.util.UUID

class ShortenUrlHandler : RequestHandler<Map<String, String>, Map<String, String>> {
    private val dynamoDB = AmazonDynamoDBClientBuilder.defaultClient()

    override fun handleRequest(input: Map<String, String>, context: Context): Map<String, String> {
        val longUrl = input["long_url"] ?: return mapOf("error" to "Should provide long_url")

        if (!isValidUrl(longUrl)) {
            return mapOf("error" to "Invalid URL")
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
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun saveItemOnDynamoDB(item: Map<String, AttributeValue>) {
        dynamoDB.putItem(PutItemRequest().apply {
            tableName = "url_shortener"
            this.item = item
        })
    }
}