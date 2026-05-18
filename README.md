# Apache Kafka: Beginner → Architect

A guided learning journey to take you from "what is Kafka?" to designing
production Kafka systems that serve 10M+ users. Every concept is paired
with an analogy first, then the technical detail.

## How to use this repo

Each module is a self-contained lesson under `modules/`. Read them in
order. After every module there are short **"check yourself"** questions —
answer them out loud before moving on. The capstone project lives under
`project/` and pulls everything together.

## Tech stack

All code in this journey is **Java + Spring**, on the latest releases:

- **Java 25 (LTS, Sept 2025)** with modern syntax — records, sealed
  interfaces, pattern matching for switch + records, text blocks,
  virtual threads, scoped values, stream gatherers, module imports,
  compact source files.
- **Spring Boot 4.x** on top of **Spring Framework 7**, with JSpecify
  null-safety annotations and the modern reactive/imperative split.
- **Spring for Apache Kafka** (`spring-kafka`) for producers, consumers,
  and Streams integration.
- **Apache Kafka 3.x / 4.x** running in **KRaft mode** (no ZooKeeper)
  via Docker Compose for local dev.
- **Maven** for build (Gradle equivalents will be noted where they differ).
- **Testcontainers** for integration tests against a real Kafka.
- **Confluent Schema Registry** + **Avro** when we get to schemas.

Once you're comfortable, we'll revisit examples in Python/Go/Kotlin so
you see the patterns are portable, but the core curriculum stays
Java + Spring.

## Roadmap

### Part 1 — Foundations (the "why" and "what")
- **Module 1** — What is Kafka and why does it exist? *(the post office analogy)*
- **Module 2** — Topics, partitions, and offsets *(the multi-lane highway)*
- **Module 3** — Brokers, the cluster, and the controller *(the warehouse network)*
- **Module 3.5** — Dev environment setup *(Docker, Spring Boot project, first run)*
- **Module 4** — Producers in depth, in Java/Spring *(how mail gets posted)*
- **Module 5** — Consumers and consumer groups, in Java/Spring *(how mail gets delivered, fairly)*

### Part 2 — Reliability and correctness
- **Module 6** — Replication, leaders, followers, and ISR *(backup mail rooms)*
- **Module 7** — Delivery semantics: at-most-once, at-least-once, exactly-once
- **Module 8** — Retention, compaction, and storage *(what stays, what goes)*

### Part 3 — The wider ecosystem
- **Module 9** — Schema Registry and Avro/Protobuf *(speaking the same language)*
- **Module 10** — Kafka Connect *(plumbing without writing code)*
- **Module 11** — Kafka Streams and ksqlDB *(real-time computation on the log)*

### Part 4 — Production architecture
- **Module 12** — Capacity planning, partitions count, and sizing
- **Module 13** — Security: TLS, SASL, ACLs
- **Module 14** — Observability: metrics, lag, alerting
- **Module 15** — Multi-region, disaster recovery, MirrorMaker
- **Module 16** — Common anti-patterns and how to avoid them

### Part 5 — Capstone project
- **Project** — Real-time event platform for a 10M-user app
  (user activity ingestion, fraud detection stream, notifications fan-out,
  analytics sink, with schema evolution, exactly-once, and DR).

## Prerequisites

You don't need Kafka experience. Helpful: basic command line, a little
Java or Python, and an idea of what HTTP and databases are.
