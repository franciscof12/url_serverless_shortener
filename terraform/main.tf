provider "aws" {
  region = "eu-west-1"
}

resource "aws_iam_role" "lambda_role" {
  name = "lambda_execution_role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_policy" "lambda_dynamodb_policy" {
  name = "lambda_dynamodb_policy"
  path = "/"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Action = [
          "dynamodb:DescribeTable",
          "dynamodb:GetItem",
          "dynamodb:Scan",
          "dynamodb:Query",
          "dynamodb:PutItem"
        ],
        Resource = "arn:aws:dynamodb:${var.aws_region}:${var.aws_account_id}:table/${var.dynamodb_table_name}"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_dynamodb" {
  policy_arn = aws_iam_policy.lambda_dynamodb_policy.arn
  role       = aws_iam_role.lambda_role.name
}

resource "aws_iam_policy" "lambda_access_policy" {
  name = "lambda_access_policy"
  path = "/"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Action = [
          "lambda:GetFunction",
          "lambda:ListFunctions",
          "lambda:InvokeFunction",
          "lambda:CreateFunction",
          "lambda:UpdateFunctionConfiguration",
          "lambda:UpdateFunctionCode",
          "lambda:DeleteFunction"
        ],
        Resource = [
          "arn:aws:lambda:${var.aws_region}:${var.aws_account_id}:function:ShortenUrlHandler",
          "arn:aws:lambda:${var.aws_region}:${var.aws_account_id}:function:RedirectUrlHandler"
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_access" {
  policy_arn = aws_iam_policy.lambda_access_policy.arn
  role       = aws_iam_role.lambda_role.name
}

resource "aws_iam_policy" "lambda_logs_policy" {
  name = "lambda_logs_policy"
  path = "/"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ],
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_logs" {
  policy_arn = aws_iam_policy.lambda_logs_policy.arn
  role       = aws_iam_role.lambda_role.name
}

resource "aws_dynamodb_table" "url_table" {
  name         = "url_shortener"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "short_id"

  attribute {
    name = "short_id"
    type = "S"
  }

  lifecycle {
    prevent_destroy = true
    ignore_changes = [name]
  }
}

resource "aws_lambda_function" "shorten_url" {
  function_name = "ShortenUrlHandler"
  runtime       = "java17"
  handler       = "ShortenUrlHandler::handleRequest"
  role          = aws_iam_role.lambda_role.arn

  filename = "../build/libs/url_shortener.jar"
  source_code_hash = filebase64sha256("../build/libs/url_shortener.jar")

  timeout = 10
  memory_size = 512

  environment {
    variables = {
      DYNAMODB_TABLE = aws_dynamodb_table.url_table.name
      AWS_GATEWAY_BASE_URL = var.aws_gateway_base_url
    }
  }
}

resource "aws_lambda_function" "redirect_url" {
  function_name = "RedirectUrlHandler"
  runtime       = "java17"
  handler       = "RedirectUrlHandler::handleRequest"
  role          = aws_iam_role.lambda_role.arn

  filename = "../build/libs/url_shortener.jar"
  source_code_hash = filebase64sha256("../build/libs/url_shortener.jar")

  timeout = 10
  memory_size = 512

  environment {
    variables = {
      DYNAMODB_TABLE = aws_dynamodb_table.url_table.name
    }
  }
}

resource "aws_api_gateway_rest_api" "url_shortener_api" {
  name        = "URLShortenerAPI"
  description = "API Gateway for el acortador de URLs"
}

resource "aws_api_gateway_resource" "shorten" {
  rest_api_id = aws_api_gateway_rest_api.url_shortener_api.id
  parent_id   = aws_api_gateway_rest_api.url_shortener_api.root_resource_id
  path_part   = "shorten"
}

resource "aws_api_gateway_method" "post_shorten" {
  rest_api_id   = aws_api_gateway_rest_api.url_shortener_api.id
  resource_id   = aws_api_gateway_resource.shorten.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "shorten_lambda" {
  rest_api_id             = aws_api_gateway_rest_api.url_shortener_api.id
  resource_id             = aws_api_gateway_resource.shorten.id
  http_method             = aws_api_gateway_method.post_shorten.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.shorten_url.invoke_arn
}

resource "aws_api_gateway_resource" "redirect" {
  rest_api_id = aws_api_gateway_rest_api.url_shortener_api.id
  parent_id   = aws_api_gateway_rest_api.url_shortener_api.root_resource_id
  path_part   = "{shortId}"
}

resource "aws_api_gateway_method" "get_redirect" {
  rest_api_id   = aws_api_gateway_rest_api.url_shortener_api.id
  resource_id   = aws_api_gateway_resource.redirect.id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "redirect_lambda" {
  rest_api_id             = aws_api_gateway_rest_api.url_shortener_api.id
  resource_id             = aws_api_gateway_resource.redirect.id
  http_method             = aws_api_gateway_method.get_redirect.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.redirect_url.invoke_arn
}

resource "aws_api_gateway_deployment" "url_shortener_deployment" {
  depends_on = [
    aws_api_gateway_integration.shorten_lambda,
    aws_api_gateway_integration.redirect_lambda
  ]

  rest_api_id = aws_api_gateway_rest_api.url_shortener_api.id
  stage_name  = "prod"
}

resource "aws_lambda_permission" "apigw_shorten" {
  statement_id  = "AllowAPIGatewayInvokeShorten"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.shorten_url.function_name
  principal     = "apigateway.amazonaws.com"

  source_arn = "${aws_api_gateway_rest_api.url_shortener_api.execution_arn}/*/POST/shorten"
}

resource "aws_lambda_permission" "apigw_redirect" {
  statement_id  = "AllowAPIGatewayInvokeRedirect"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.redirect_url.function_name
  principal     = "apigateway.amazonaws.com"

  source_arn = "${aws_api_gateway_rest_api.url_shortener_api.execution_arn}/*/GET/{shortId}"
}

output "api_gateway_url" {
  value = aws_api_gateway_deployment.url_shortener_deployment.invoke_url
}