# Define all hardcoded local variable and local variables looked up from data resources
locals {
  stack_name                 = "company-requests" # this must match the stack name the service deploys into
  name_prefix                = "${local.stack_name}-${var.environment}"
  global_prefix              = "global-${var.environment}"
  service_name               = "strike-off-partner-objections-api"
  container_port             = "8080" # default Java port to match start script
  docker_repo                = "strike-off-partner-objections-api"
  lb_listener_rule_priority  = 51                                               # priority for the lb listener rule to route to this service, must be unique across services using the same load balancer listener
  lb_listener_paths          = ["/company/*/strike-off-partner-objections*", "/strike-off-partner-objections-api/healthcheck"]      # paths to route to the service, must be unique across services using the same load balancer listener
  healthcheck_path           = "/strike-off-partner-objections-api/healthcheck" #healthcheck path for strike-off-partner-objections-api service
  healthcheck_matcher        = "200"
  application_subnet_ids     = data.aws_subnets.application.ids
  kms_alias                  = "alias/${var.aws_profile}/environment-services-kms"
  service_secrets            = jsondecode(data.vault_generic_secret.service_secrets.data_json)
  stack_secrets              = jsondecode(data.vault_generic_secret.stack_secrets.data_json)
  application_subnet_pattern = local.stack_secrets["application_subnet_pattern"]
  use_set_environment_files  = var.use_set_environment_files
  app_environment_filename   = "strike-off-partner-objections-api.env"
  vpc_name                   = local.stack_secrets["vpc_name"]

  # Enable Eric
  use_eric_reverse_proxy = true
  eric_port              = "10000" # container port plus 1

  service_secrets_arn_map = {
    for sec in module.secrets.secrets :
    trimprefix(sec.name, "/${local.service_name}-${var.environment}/") => sec.arn
  }

  global_secret_list = flatten([for key, value in local.global_secrets_arn_map :
    { "name" = upper(key), "valueFrom" = value }
  ])

  global_secrets_arn_map = {
    for sec in data.aws_ssm_parameter.global_secret :
    trimprefix(sec.name, "/${local.global_prefix}/") => sec.arn
  }

  service_secret_list = flatten([for key, value in local.service_secrets_arn_map :
    { "name" = upper(key), "valueFrom" = value }
  ])

  ssm_service_version_map = [
    for sec in module.secrets.secrets : {
      name  = "${replace(upper(local.service_name), "-", "_")}_${var.ssm_version_prefix}${replace(upper(basename(sec.name)), "-", "_")}",
      value = tostring(sec.version)
    }
  ]

  ssm_global_version_map = [
    for sec in data.aws_ssm_parameter.global_secret : {
      name  = "GLOBAL_${var.ssm_version_prefix}${replace(upper(basename(sec.name)), "-", "_")}",
      value = tostring(sec.version)
    }
  ]

  task_secrets = concat(local.global_secret_list, local.service_secret_list)

  task_environment = concat(local.ssm_global_version_map, local.ssm_service_version_map)

  # get eric secrets from global secrets map
  eric_secrets = [
    { "name" : "API_KEY", "valueFrom" : local.global_secrets_arn_map.eric_api_key },
    { "name" : "AES256_KEY", "valueFrom" : local.global_secrets_arn_map.eric_aes256_key }
  ]

  eric_environment_filename = "eric.env"
}
