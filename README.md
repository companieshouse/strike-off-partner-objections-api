# strike-off-partner-objections-api

API service for processing strike-off objections and withdrawals submitted by partner organisations.

Incoming objections and withdrawals are validated, persisted to the appropriate MongoDB collections, and published to Kafka topics for downstream processing by the Objections Processor Service.

---
## Overview

The service is responsible for:

- Receiving strike-off objection and withdrawal requests from partner organisations
- Validating incoming payloads and request data
- Persisting requests to MongoDB
- Publishing events to Kafka for asynchronous downstream processing

---
## Related Services

- [strike-off-partner-objections-processor](https://github.com/companieshouse/strike-off-partner-objections-processor)

## Technology Stack
- Java 21
- Spring Boot
- Maven
- MongoDB
- Apache Kafka
---

## Requirements

To build the `strike-off-partner-objections-api`, you will need:
* [Git](https://git-scm.com/downloads)
* [Java 21](https://www.oracle.com/uk/java/technologies/downloads/#java21)
* [Maven](https://maven.apache.org/download.cgi)
* Internal Companies House core services

You will also need a REST client (e.g. Postman or Bruno) if you wish to interact with the `strike-off-partner-objections-api` service endpoints.

## Sensitive response cache policy

Responses reaching the MVC interceptor chain for the following application route
families receive `Cache-Control: no-store` and `Pragma: no-cache`:

- `/company/{company_number}/strike-off-partner-objections` and all descendants
- `/company/{company_number}/strike-off-partner-objections-withdrawals` and all descendants
- The corresponding `/internal/company/{company_number}/...` route families

The mandatory cache interceptor runs before authentication and authorisation, so
the policy also covers creation responses, internal status updates and rejected
requests reaching that chain. Future endpoints within these route families
inherit the policy. No collection-list GET operations are added by this policy.
Health, readiness, liveness, monitoring and unrelated status routes are unchanged.

Errors produced before MVC invokes the interceptor chain, such as some routing
or unsupported-method errors, are outside this policy's guarantee. The headers
instruct compliant clients and intermediaries not to store future responses;
they do not purge previously cached content or force misconfigured caches to
comply. Infrastructure cache review and any required purge remain separate
rollout considerations for the security owner.

The Postman collection includes shared, route-scoped assertions for both exact
header values, including detection of duplicate headers, alongside its existing
request assertions. For manual acceptance, run the affected requests against the
normal gateway-facing URL to verify the headers visible to clients, covering
successful requests, validation errors, authentication/authorisation rejections
and internal updates. Health requests are excluded from these shared assertions.
Java tests and collection-script checks do not replace this live acceptance run.