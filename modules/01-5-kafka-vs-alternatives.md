# Module 1.5 — Kafka vs. the alternatives

> "Why are there so many of these tools? Why isn't there just one?"

Because they all solve **different shapes** of the same broad problem
("get data from A to B reliably"). One tool optimized for everything
would be mediocre at all of them. This module is the map of the
landscape, with the analogy for each, so you know *when* to reach for
Kafka and when to reach for something else.

## 1.5.1 First, the right mental categories

Most beginners lump these tools together as "messaging." They're not.
There are really **four different categories**:

| Category | What it does | Examples |
|---|---|---|
| **Work queue** | Distribute *tasks* to workers; each task done **once** | RabbitMQ (classic), AWS SQS, ActiveMQ |
| **Event log / streaming platform** | Durable, replayable stream of *events*; many independent readers | **Kafka**, Apache Pulsar, AWS Kinesis, Google Pub/Sub, Azure Event Hubs |
| **Lightweight pub/sub / messaging fabric** | Very fast in-memory pub/sub, often for microservice comms | NATS, Redis Pub/Sub, ZeroMQ |
| **Stream processor** | *Computes* on a stream (joins, windows, aggregations). Not transport. | Apache Flink, Spark Streaming, Kafka Streams, ksqlDB |

Stream processors are not competitors to Kafka — they typically **run
on top of** Kafka. So when we say "alternatives to Kafka," we really
mean other tools in categories 1–3.

## 1.5.2 The lunch analogy (one analogy, all tools)

Imagine you're feeding a cafeteria of people.

- **Work queue (RabbitMQ, SQS):** the lunch counter has a **ticket
  dispenser**. Each diner pulls a number; one chef makes one plate
  for that number. The ticket is consumed — gone. Perfect for tasks
  ("send this email", "resize this image") where each task should be
  done by exactly one worker.

- **Event log (Kafka, Pulsar, Kinesis):** the cafeteria has a **giant
  notice board** at the entrance. Every order placed today is pinned
  to it, in order, and stays for a week. The chef reads it. The
  manager reads it. The auditor reads it. The new analytics intern
  who joins next week can still go read last week's board. **No one
  removes the notes.**

- **Lightweight pub/sub (NATS, Redis):** a **walkie-talkie network**.
  You shout "table 4 needs water" and whoever is listening *right now*
  hears it. Blink and miss it. Very fast, very cheap, no replay.

- **Stream processor (Flink, Streams):** the **head chef** who stands
  in front of the notice board and continuously computes things like
  "average wait time in the last 5 minutes" or "top 3 dishes per hour"
  by reading and combining notes as they appear.

Once you internalize that picture, every tool below slots into it.

## 1.5.3 The tools, one by one

### Kafka (the protagonist of this course)
**What it is:** a distributed, partitioned, replicated **append-only
log**. High throughput (millions/sec), durable, replayable.

**Sweet spot:**
- Event sourcing — your business events are the source of truth.
- Fan-out to many independent consumers (email, analytics, ML, audit).
- Stream processing pipelines.
- Bridging microservices without point-to-point coupling.
- Buffering between fast producers and slower downstreams.

**Where it hurts:**
- Per-message routing rules (header-based dispatch) — not its strength.
- Low-volume request/reply where you want simple "send this one task to
  one worker."
- Tiny teams who don't want to operate a cluster (use managed: MSK,
  Confluent Cloud).
- Workloads that need **per-message priority** or **selective acks** —
  Kafka has no concept of priority lanes.

**Analogy:** the giant notice board at the cafeteria entrance.

### RabbitMQ
**What it is:** a classic **AMQP message broker**. Producers send
messages to **exchanges**, exchanges route by rules to **queues**,
consumers pull from queues. Messages disappear once acked.

**Sweet spot:**
- Task queues: image resizing, email sending, PDF generation.
- Complex routing — "send this message only to consumers tagged X."
- Request/reply with reply queues.
- Lower-volume but feature-rich messaging (per-message TTL, priorities,
  dead-letter queues, scheduled delivery).

**Where it hurts:**
- Replay of past messages — not its model. Once consumed, gone.
- Very high throughput (Kafka outperforms it by orders of magnitude on
  raw write rate).
- Long retention. RabbitMQ in front of a slow consumer fills up and
  pushes back.

**Analogy:** a **smart post office with routing rules**. The clerk
looks at the envelope and decides which mailbox it goes into, even
based on a sticker on the envelope. Mail leaves once picked up.

**Kafka vs RabbitMQ in one line:** RabbitMQ is a **broker that routes
and dispatches**; Kafka is a **log that retains and replays**. If you
ever say "we need to replay the last 24 hours of events," you want
Kafka. If you say "I need to dispatch this task to whichever worker
is free, with retries and dead-lettering," you want RabbitMQ.

### Amazon SQS
**What it is:** AWS-managed simple queue. Two flavors: **Standard**
(at-least-once, unordered, massive scale) and **FIFO** (ordered,
exactly-once-ish per message group, lower throughput).

**Sweet spot:**
- "I'm in AWS and I need a queue tomorrow, with zero ops."
- Decoupling Lambda triggers from upstream services.
- Buffering between AWS services.

**Where it hurts:**
- No fan-out to multiple independent consumers (you pair it with SNS
  for that — "SNS+SQS" is the AWS fan-out pattern).
- No replay.
- Max retention 14 days.
- Each message consumed once → can't have email *and* analytics both
  read the same message without duplicating into two queues.

**Analogy:** the **ticket dispenser at the deli counter**, run by AWS,
and AWS keeps it stocked, cleaned, and running. You don't even see
the dispenser.

### Amazon Kinesis Data Streams
**What it is:** AWS's Kafka-equivalent. Same mental model — durable,
partitioned, replayable log. Different API and operations model.

**Sweet spot:**
- You're an AWS-only shop and want a Kafka-like log without running
  Kafka.
- Tight integration with AWS analytics services (Firehose → S3,
  Lambda triggers, Glue, Redshift).

**Where it hurts:**
- Per-shard limits are lower than per-partition limits on Kafka
  (1 MB/s write, 2 MB/s read per shard).
- 24h–365d retention (you pay more for longer).
- Smaller open-source ecosystem; Confluent's Kafka tooling doesn't
  apply.
- Vendor lock-in to AWS.

**Analogy:** the giant notice board, but **built by AWS, only readable
inside AWS premises**, billed per square foot per day.

### Google Cloud Pub/Sub
**What it is:** Google's managed **global pub/sub**. Topic + many
subscriptions. Auto-scales, no partition-count to choose.

**Sweet spot:**
- GCP-native event distribution.
- Global, multi-region by default.
- "I don't want to think about partitions."

**Where it hurts:**
- Ordering only via "ordering keys" (similar idea to Kafka keys, but
  more limited).
- Replay is recent-only (7 days max with snapshots/seek).
- Less control over throughput tuning.
- Lock-in to GCP.

**Analogy:** an **intercom system in a Google office** — anyone
subscribed to a channel hears anything announced on it; you can rewind
a little but not far.

### Azure Event Hubs
**What it is:** Microsoft's Kafka-equivalent. Actually exposes a
**Kafka-compatible API** (you can point Kafka clients at it).

**Sweet spot:**
- Azure-native, especially with Azure Functions and Stream Analytics.
- Want to use Kafka clients but not run Kafka.

**Where it hurts:**
- Some Kafka features missing or differ (transactions, compacted
  topics).
- Per-partition throughput limits.

**Analogy:** AWS Kinesis, but for Azure.

### Apache Pulsar
**What it is:** Kafka's main open-source competitor. **Separates
storage (BookKeeper) from compute (broker)**, allowing independent
scaling. Multi-tenancy is first-class.

**Sweet spot:**
- True multi-tenant platforms where many teams share one cluster.
- Geo-replication built-in.
- Mix of queueing (Pulsar has both queue and stream semantics) and
  streaming in one system.
- Tiered storage to S3 is mature.

**Where it hurts:**
- Smaller ecosystem and community than Kafka (most jobs ask for Kafka).
- More moving parts to operate (BookKeeper bookies + brokers +
  ZooKeeper).
- Tooling and managed offerings are fewer.

**Analogy:** Kafka **redesigned in 2020** — the notice board's
*backing storage* (BookKeeper) is in a separate room from the *clerk
who reads/writes* (broker), so you can add more clerks without buying
more shelves.

### Redis Streams
**What it is:** Streams data type inside Redis. In-memory log with
consumer groups.

**Sweet spot:**
- Sub-millisecond latency on smallish streams.
- You already run Redis.
- Replayable for a *short* window.

**Where it hurts:**
- Bound to one Redis cluster's memory — not for TBs of events.
- Not designed for petabyte-scale or long retention.

**Analogy:** a **whiteboard near the kitchen** — fast to write, fast
to read, you erase older stuff when it fills up.

### NATS / NATS JetStream
**What it is:** ultra-light pub/sub messaging. JetStream is the
persistence layer that adds Kafka-like durability.

**Sweet spot:**
- Microservice-to-microservice messaging at very low latency.
- Edge / IoT topologies where Kafka is too heavy.
- Request/reply patterns with built-in correlation.

**Where it hurts:**
- Smaller community than Kafka, fewer connectors.
- JetStream is newer and the production-at-scale stories are fewer.

**Analogy:** the **walkie-talkie network**. JetStream adds a tape
recorder to it.

### ActiveMQ / IBM MQ
**What they are:** the JMS era of enterprise messaging. Still alive
in big banks and telcos.

**Sweet spot:**
- Existing JMS-based Java enterprise apps.
- Transactional integration with XA-distributed transactions and
  legacy mainframes.

**Where it hurts:**
- Throughput, modern tooling, cloud-native deployment.
- Hiring — fewer engineers excited to work on them.

**Analogy:** the **typewriter** of messaging. Reliable, well-understood,
but not what you'd pick for a new system.

### AWS SNS / EventBridge
**Not the same category, but often confused.** These are *event
routers* — fan out one event to many endpoints (Lambda, email, HTTP,
SQS) using rules. No replay, no long retention.

Use them when the routing **rules** are the value and you don't need a
durable log.

### Stream processors (Flink, Spark Streaming, Kafka Streams, ksqlDB)
These do **not transport** messages. They *compute* on streams.

- **Apache Flink** — the gold standard for low-latency, stateful
  stream processing, exactly-once across complex graphs, event-time
  windows, large state.
- **Spark Streaming / Structured Streaming** — micro-batch processing,
  natural fit if you're already a Spark shop.
- **Kafka Streams** — a **library** (just a jar in your Spring Boot
  app) for processing Kafka topics. No separate cluster. Sweet spot
  for "consume topic A, transform, write topic B." We'll use it in
  later modules.
- **ksqlDB** — SQL on top of Kafka Streams. Great for ad-hoc and
  for teams that prefer SQL over Java.

You almost always pair one of these *with* Kafka.

## 1.5.4 Why do all these exist?

Three honest reasons:

1. **Different optimization points.** A tool tuned for sub-ms latency
   (NATS) cannot also be tuned for petabyte-scale append-only logs
   (Kafka). They make opposite trade-offs on memory, disk, and
   durability.

2. **Cloud vendor lock-in.** Each cloud wants you to use *their*
   managed service: Kinesis, Pub/Sub, Event Hubs. They're broadly
   equivalent to Kafka.

3. **Historical evolution.**
   - 1990s–2000s: **JMS era** — ActiveMQ, IBM MQ. Transactions, XA,
     mainframes.
   - 2007–2015: **AMQP era** — RabbitMQ. Smart routing, language
     diversity.
   - 2011–today: **Log era** — Kafka, Pulsar, Kinesis. High throughput,
     replay, event sourcing.
   - 2018–today: **Cloud-native event era** — Pub/Sub, EventBridge,
     serverless triggers.

Each era didn't kill the previous — it added a new category.

## 1.5.5 A decision flowchart

```
Do you need to REPLAY past data, or fan out to many independent readers?
├── Yes ──▶ Event log:
│           ├── On AWS, want managed?     → Kinesis (or MSK = managed Kafka)
│           ├── On GCP, want managed?     → Pub/Sub
│           ├── On Azure, want managed?   → Event Hubs (Kafka API)
│           ├── Multi-cloud or on-prem?   → Kafka (Confluent / self-host)
│           └── Multi-tenant platform?    → Pulsar
│
└── No ──▶ You probably want a queue or pub/sub:
           ├── Task queue, complex routing, low/medium volume?
           │                                → RabbitMQ
           ├── Just need a cloud queue, AWS shop?
           │                                → SQS (+ SNS for fan-out)
           ├── Microservice messaging, want low latency?
           │                                → NATS
           ├── Already run Redis, small scale?
           │                                → Redis Streams
           └── Existing JMS Java app?      → ActiveMQ
```

## 1.5.6 Worked examples — same problem, different tool

**Problem A:** "We have 100k events/sec of user clicks. Email,
analytics, ML, audit, and a future fraud team all need them."
- **Right tool:** Kafka (or Kinesis/Pulsar/Event Hubs). Fan-out and
  replay are the *whole point*.

**Problem B:** "We need to send password-reset emails. Order doesn't
matter. Each email goes to one worker. Maybe 100/sec."
- **Right tool:** RabbitMQ or SQS. A log is overkill — you want
  retries, DLQ, ack semantics.

**Problem C:** "We have 30 microservices and want a low-latency
request/reply backbone with subjects like `orders.create`."
- **Right tool:** NATS. Kafka is the wrong shape (it's a log, not a
  request/reply fabric).

**Problem D:** "We're a Google Cloud Run shop and want to trigger
functions on events."
- **Right tool:** Pub/Sub. It's already wired into the platform.

**Problem E:** "We want to maintain a real-time leaderboard from a
firehose of game events, with windowed aggregation."
- **Right tools:** Kafka **plus** a stream processor (Flink or
  Kafka Streams). The leaderboard is the *processing* job, Kafka
  is the transport.

**Problem F:** "Our bank has a 25-year-old Java system using JMS and
XA transactions to talk to a mainframe."
- **Right tool:** Stay on IBM MQ / ActiveMQ. Don't rewrite the
  world just for tech fashion. (You can still add Kafka *alongside*
  for the modern parts.)

## 1.5.7 Common myths to retire

- **"Kafka is just a faster RabbitMQ."** No. Different shape. Kafka
  is a log; RabbitMQ is a broker.
- **"You can replay messages in RabbitMQ."** Not really — you can use
  dead-letter exchanges and message TTLs, but it's not designed for
  replay.
- **"NATS replaced Kafka."** No. They solve different problems.
- **"Pulsar is just better Kafka."** It has nice properties (storage
  separation, multi-tenancy), but the ecosystem and hiring pool are
  much smaller. In 2026, Kafka is still the default.
- **"Use Flink instead of Kafka."** Flink reads from Kafka. They're
  not substitutes.

## 1.5.8 Check yourself

1. You're designing a system where every order placed must trigger
   *exactly one* shipping label print, with automatic retries. Would
   you reach for Kafka or RabbitMQ? Why?
2. Your manager says "we already have AWS SQS, why do we need Kafka?"
   Give two reasons that SQS can't replace Kafka for your event
   pipeline.
3. A teammate proposes using NATS to store the last 30 days of audit
   events. What's wrong with that choice?
4. Why is Apache Flink *not* a Kafka alternative?

Next up: back to **Module 2 — Topics, partitions, and offsets** (the
multi-lane highway), now that you know *which* tool we're going deep
on and why.
