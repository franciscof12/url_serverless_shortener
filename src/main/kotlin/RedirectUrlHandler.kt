import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.GetItemRequest
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler

class RedirectUrlHandler : RequestHandler<Map<String, String>, Map<String, String>> {
    private val dynamoDB = AmazonDynamoDBClientBuilder.defaultClient()

    override fun handleRequest(input: Map<String, String>, context: Context): Map<String, String> {
        val shortId = input["short_id"] ?: return mapOf("error" to "Should provide short_id")

        val originalUrl = getOriginalUrlFromDynamoDB(shortId) ?: return mapOf("error" to "URL not found")

        return mapOf("long_url" to originalUrl)
    }

    private fun getOriginalUrlFromDynamoDB(shortId: String): String? {
        val request = getItemRequest(shortId)

        val item = dynamoDB.getItem(request).item ?: return null

        return item["long_url"]?.s
    }

    private fun getItemRequest(shortId: String): GetItemRequest {
        val request = GetItemRequest().apply {
            tableName = "url_shortener"
            key = mapOf("short_id" to AttributeValue(shortId))
        }
        return request
    }
}