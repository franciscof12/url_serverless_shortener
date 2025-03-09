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
        val shortId = UUID.randomUUID().toString().substring(0, 6)

        var item = mapOf(
            "short_id" to AttributeValue(shortId),
            "long_url" to AttributeValue(longUrl)
        )

        dynamoDB.putItem(PutItemRequest().apply {
            tableName = "url_shortener"
            item = item
        })

        val apiGatewayBaseUrl = "https://your-api-id.execute-api.us-east-1.amazonaws.com/prod"
        val shortUrl = "$apiGatewayBaseUrl/$shortId"

        return mapOf("short_url" to shortUrl)
    }
}