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