variable "aws_account_id" {
  description = "AWS Account ID"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  default     = "eu-west-1"
}

variable "dynamodb_table_name" {
  description = "DynamoDB table name"
  default     = "url_shortener"
}

variable "aws_gateway_base_url" {
  description = "API Gateway base URL"
  default     = "https://execute-api.region.amazonaws.com/"
}