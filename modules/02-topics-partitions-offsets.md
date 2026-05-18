# Module 2 — Topics, partitions, and offsets

In Module 1 we said Kafka is a giant ordered log. In this module we
zoom in on the three terms that come up in *every* Kafka conversation:
**topic**, **partition**, **offset**. Get these straight and 70% of
Kafka makes sense immediately.

## 2.1 The multi-lane highway analogy

Picture a long, multi-lane highway:

- The **highway** is a **topic** (e.g. `orders.placed`).
- Each **lane** is a **partition**.
- Each **car** in a lane is a **message** with a position number — its
  **offset**.

Important consequences of this picture:

1. Cars in *one lane* stay in order. The blue Tesla that entered lane 2
   before the red Honda will exit lane 2 first. Always.
2. Cars in *different lanes* have **no order between each other**. The
   yellow Jeep in lane 0 might be ahead of or behind the blue Tesla
   in lane 2 — Kafka doesn't care.
3. You can add more lanes (partitions) to handle more cars
   (throughput). But **once added you can't remove them**, and adding
   them reshuffles which car goes into which lane going forward.

That last point is the source of most beginner partition-count regrets.
We'll come back to it.

## 2.2 What exactly is a topic?

A **topic** is just a *named* logical channel. It has no inherent
storage — it's the partitions underneath it that hold data.

Naming convention (use these from day one):
```
<domain>.<entity>.<event>     e.g. payments.invoice.paid
<domain>.<entity>.<state>     e.g. inventory.product.snapshot
```
Use dots, lowercase, plural-or-singular consistently, and **never** put
the consumer's name in the topic. The topic describes the *event*, not
who reads it.

Topics also have **configuration** attached:
- `num.partitions` — how many partitions to create
- `replication.factor` — how many copies of each partition (Module 6)
- `retention.ms` — how long messages live (Module 8)
- `cleanup.policy` — `delete` (time-based) or `compact` (keep latest
  per key) (Module 8)
- `min.insync.replicas` — durability floor (Module 6/7)

## 2.3 What exactly is a partition?

A partition is the **physical unit of storage, ordering, and
parallelism**. Three roles in one:

### Role 1: storage unit
Each partition is a set of files (segments) on a broker's disk. If
`orders.placed` has 6 partitions and replication factor 3, that's
**18 partition replicas** distributed across the brokers.

### Role 2: ordering unit
Order is guaranteed **only within a single partition**. Two messages
with the same key always go to the same partition, so they stay in
order relative to each other. Two messages with different keys *might*
be in different partitions.

This is why **picking the right partition key is an architectural
decision**, not a coding detail. Examples:

| Use case                          | Good key                | Why                                   |
|-----------------------------------|-------------------------|---------------------------------------|
| Per-user activity stream          | `user_id`               | Events for user 42 stay in order      |
| Order lifecycle (placed/paid/shipped) | `order_id`          | Each order's events stay in order     |
| Bank transactions per account     | `account_id`            | Balance math stays consistent         |
| Pure firehose with no order need  | `null` (round-robin)    | Spreads load evenly                   |

Anti-patterns:
- Using `country_code` as a key → everyone in "US" goes to one
  partition → **hot partition**, the rest sit idle.
- Using `timestamp` as a key → only the partition for "now" is hot.
- Using `null` when you *do* need order → events for the same order
  arrive in random sequence and your state machine breaks.

### Role 3: parallelism unit
Inside one consumer group, **a partition is consumed by at most one
consumer instance**. This is the rule that defines Kafka's parallelism.

```
topic orders.placed: partitions 0..5  (6 partitions)
consumer group "fraud-checker" with 3 instances:
    instance A → partitions 0, 1
    instance B → partitions 2, 3
    instance C → partitions 4, 5
```

If you scale the consumer group to 6 instances, each one gets exactly
one partition. If you scale to **9 instances**, three of them sit idle
— there's nothing to give them. Therefore:

> **The number of partitions sets the upper bound on how much you can
> parallelize consumption.**

This is the single most important architectural number you pick when
creating a topic.

## 2.4 What exactly is an offset?

An offset is a **monotonically increasing 64-bit integer** that Kafka
assigns to a record when it's appended to a partition. It is **local to
the partition** — partition 0 has its own offset 1234, partition 1 has
its own offset 1234, and they're unrelated records.

```
partition 0: ... [off 1200] [off 1201] [off 1202] ...
partition 1: ... [off 9876] [off 9877] [off 9878] ...
partition 2: ... [off    7] [off    8] [off    9] ...
```

Three offsets matter in practice:

- **Log-start offset** — the oldest offset still retained (older ones
  have been deleted).
- **High-water mark** — the latest offset that's been replicated to all
  in-sync replicas. Consumers can only read up to here.
- **Committed offset** (per consumer group, per partition) — "I've
  processed up to and including offset N". Stored by Kafka itself in
  the internal topic `__consumer_offsets`.

**Lag** = `high-water mark` − `committed offset`. That's the metric
your on-call cares about. If lag grows unbounded, consumers can't keep
up.

## 2.5 How a producer picks a partition

When a producer sends a record, Kafka's default `DefaultPartitioner`
decides like this:

1. If the record has an **explicit partition number**, use it.
2. Else if it has a **key**, partition =
   `murmur2(key) mod num.partitions`. Same key → same partition,
   **as long as the partition count doesn't change**.
3. Else (no key, no explicit partition), use the **sticky partitioner**
   to batch records into the same partition for a while, then switch.
   This is round-robin-ish and optimizes for batching.

Critical gotcha: **the key→partition mapping depends on
`num.partitions`**. If you have 6 partitions and add 6 more to make 12,
keys will re-hash. New records for `user_id=42` will go to a different
partition than old records for `user_id=42`. Order across the
transition is broken. This is why **growing partition count is a
careful operation**, not a casual one.

## 2.6 Architectural decision: how many partitions?

There is no perfect formula, but here is the working framework
experienced architects use:

1. **Target throughput.** Estimate peak write rate (e.g. 50,000
   records/sec) and average record size (e.g. 1 KB). That's ~50 MB/s.

2. **Per-partition limits.** A healthy partition handles ~10 MB/s
   write and ~20 MB/s read on commodity hardware. Set your
   per-partition target at, say, 5–10 MB/s for headroom.

3. **Consumer parallelism.** What's the maximum number of consumer
   instances you ever want? You need at least that many partitions.

4. **Take the max.** `partitions = max(throughput/perPartition,
   maxConsumerInstances)`.

5. **Round up generously**, but not crazily. Each partition has cost:
   open files, replication overhead, controller load. A cluster with
   200,000+ partitions starts to suffer. For one topic, 12–200 is a
   common production range.

Worked example for the 10M-user app capstone:
- Active users at peak: ~500k concurrent.
- Avg events/user/min during activity: ~6 → 50k events/sec peak.
- Each event ~500 bytes → 25 MB/s.
- At 5 MB/s per partition → need ~5 partitions for throughput.
- But we want to scale consumer pods to 30 during traffic spikes.
- → pick **30 partitions** (consumer parallelism dominates).

You can always start a *little* high; starting low and growing later is
painful (see 2.5 gotcha).

## 2.7 What this looks like with the Kafka CLI

You'll meet these commands constantly. Examples assume a local broker
at `localhost:9092`:

```bash
# create a topic
kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic orders.placed \
  --partitions 6 --replication-factor 3 \
  --config retention.ms=604800000 \
  --config min.insync.replicas=2

# describe it
kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic orders.placed

# grow partitions (CAREFUL — re-hashes keys)
kafka-topics.sh --bootstrap-server localhost:9092 \
  --alter --topic orders.placed --partitions 12
```

We'll wire this up in Module 3.5 with Docker Compose so you can run it
yourself.

## 2.8 How this maps to Spring (preview)

In Spring Boot you almost never call the raw API. You describe topics
declaratively and Spring's `KafkaAdmin` reconciles them on startup:

```java
@Configuration
public class TopicConfig {

    @Bean
    NewTopic ordersPlaced() {
        return TopicBuilder.name("orders.placed")
            .partitions(30)
            .replicas(3)
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
            .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")
            .build();
    }
}
```

That's a real Java 21 + Spring Boot 3 snippet. Don't worry about
running it yet — we'll build a full project in Module 3.5.

## 2.9 Check yourself

1. You're designing `payments.events`. Order matters per **account**.
   You expect 200k events/sec at peak. What partition count would you
   start with, and what key would you use?
2. A topic has 4 partitions. Your consumer group has 6 instances.
   What's the worst outcome and why?
3. You realize after launch that 4 partitions wasn't enough. You
   double it to 8. Your downstream service starts seeing duplicate /
   out-of-order events for the same account. What happened?
4. What's the difference between the **high-water mark** and a
   consumer group's **committed offset**? Which one defines lag?

Next up: **Module 3 — Brokers, the cluster, and the controller** —
how the post offices actually work together to make all of this
durable.
