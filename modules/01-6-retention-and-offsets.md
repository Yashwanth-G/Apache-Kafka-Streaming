# Module 1.6 — When does the paper leave the box? Retention & offsets

This module answers the three questions every beginner asks after
Module 1:

1. How long does an event stay in Kafka?
2. How does Kafka know that all the services (email, SMS, loyalty,
   WhatsApp…) have consumed an event?
3. When is the event removed?

The answer is the biggest mental shift from RabbitMQ / SQS, so we put
it up front before going deeper.

## 1.6.1 The shocking one-line answer

> **Kafka does NOT track who has consumed what. It does NOT remove
> records when they are consumed. Each record stays for a fixed time
> (default 7 days) and is then deleted whether or not anyone read it.**

Read that twice. If RabbitMQ is your prior mental model, this will
feel wrong at first. It is correct, and it's *the* property that lets
Kafka do high-throughput fan-out, replay, and "onboard a new consumer
that reads last week's data" — all things a queue can't do.

## 1.6.2 Why it works this way

In Module 1.5 we drew the line:

> RabbitMQ is a **broker that routes and dispatches**.
> Kafka is a **log that retains and replays**.

A broker has to know "who has ack'd this message" to know when to free
the slot. A log doesn't — it's just an append-only sequence. The
broker model is fundamentally a per-message bookkeeping problem; the
log model is fundamentally a per-reader bookkeeping problem. Kafka
chose the second, which scales much better.

If Kafka *did* track every consumer's ack for every message, two
things would die:
- **Throughput.** Per-message broker bookkeeping is the slow part of
  RabbitMQ. Kafka skips it entirely and gets millions/sec.
- **Open-ended fan-out.** A new consumer that joins next year would
  have to register with the broker in advance. With Kafka, the broker
  doesn't know or care it exists.

## 1.6.3 So how do consumers know where they are?

Each consumer keeps its own **bookmark** — a 64-bit integer per
partition called a **consumer offset**.

```
notice board (topic "orders.placed", partition 0):
  paper #1000   paper #1001   paper #1002   paper #1003   ◀ newest

bookmarks (stored by Kafka itself in __consumer_offsets):
  group "email-sender"      → 1003   (caught up)
  group "sms-sender"        → 1001   (2 behind)
  group "loyalty-updater"   → 950    (53 behind — was offline)
  group "whatsapp-sender"   → 500    (joined late, catching up)
```

Key facts:

- A **consumer group** is a named team of consumer instances that
  share work. Each group has its own offsets.
- Groups are **independent**. Email being caught up tells you nothing
  about loyalty.
- Offsets live in an **internal Kafka topic** called
  `__consumer_offsets`. Yes — Kafka stores its own bookmarks inside
  itself. It's logs all the way down.
- A consumer **commits** its offset periodically ("I've processed
  through 1003"). Commit cadence is a tunable trade-off between
  duplicate-on-crash and offset-storage cost.

### Three implications you must internalize

1. **Readers are independent.** A slow loyalty service does not slow
   down email. They each walk the log at their own pace.
2. **Replay is free.** A brand-new "WhatsApp" consumer joins next
   week and asks to start from the oldest surviving offset. It will
   read all of last week's orders. No existing service has to do
   anything.
3. **Slow consumers can fall off the end.** If loyalty is down for
   8 days and retention is 7 days, the data it missed on day 1 is
   gone. Kafka silently advances loyalty's offset to the oldest
   surviving record. Those messages are **lost to that consumer**.

Implication 3 is the source of most "we lost data!" Kafka incidents.
The data wasn't lost — it expired by config, and a downstream was
offline too long.

## 1.6.4 When does Kafka actually delete a record?

You configure cleanup **per topic**. Two policies, optionally combined.

### Policy A — `cleanup.policy = delete` (the default)

Records are removed when they are either too old or the partition is
too big.

| Config | Default | Meaning |
|---|---|---|
| `retention.ms` | `604800000` (**7 days**) | Keep records at least this long |
| `retention.bytes` | `-1` (unlimited) | Maximum size per **partition** before old records are deleted |
| `segment.bytes` | `1 GB` | Records are stored in **segments**; only full, closed segments can be deleted |
| `segment.ms` | `7 days` | Roll a new segment at least this often |

Whichever limit hits first wins. Deletion happens at **segment
granularity** — Kafka doesn't delete record-by-record; it drops whole
segment files. (This is why Kafka can delete TB of data in
milliseconds.)

Common production settings:

| Topic type | `retention.ms` | Why |
|---|---|---|
| High-volume click stream | 1–3 days | Cheap, fan-out only, downstreams are caught up minute-by-minute |
| Orders / payments | 7–30 days | Allow replay during incident response |
| Audit / compliance | 30–90 days, then S3 | Long retention via tiered storage, not Kafka memory |
| Test / dev | a few hours | Don't waste disk |

### Policy B — `cleanup.policy = compact`

Instead of deleting by time, Kafka periodically scans the partition
and **keeps only the latest record per key**. Older records with the
same key are removed.

```
Before compaction (key=user_id):
  off 100  (user_42, email="old1@x.com")
  off 101  (user_99, email="...")
  off 200  (user_42, email="old2@x.com")
  off 300  (user_42, email="new@x.com")   ◀ latest for user_42

After compaction:
  off 101  (user_99, ...)
  off 300  (user_42, email="new@x.com")
```

Used when the topic represents a **current-state snapshot**, not an
event log:

- User profile topic — keep the latest profile per user, forever.
- Product catalog snapshot — latest product details per SKU.
- Configuration topic — latest config per feature flag.

A common pattern: `cleanup.policy = compact,delete`. Keep the latest
per key, *and* drop records older than 90 days even if they're the
latest. Good for "current state + reasonable history" hybrids.

### Tombstones (deleting a key in a compacted topic)

To "delete" a key in a compacted topic, you write a record with that
key and a **null value**. This is called a **tombstone**. After a
configurable delay (`delete.retention.ms`, default 24h), the tombstone
itself is removed, and the key is gone forever.

## 1.6.5 What this means for your design

When you create a new topic, you decide three things up front:

1. **Cleanup policy:** event log (`delete`) or snapshot
   (`compact`) or both?
2. **Retention window:** how long do consumers need to be able to
   recover from being down, or to be able to replay history?
3. **Worst-case-downtime budget:** retention must cover your worst
   tolerable outage of any downstream service for that topic.

Worked example for the pizza shop:

| Topic | Policy | Retention | Reasoning |
|---|---|---|---|
| `orders.placed` | `delete` | 7 days | Allow replay for incident response and onboarding new consumers |
| `payments.events` | `delete` | 30 days | Finance reconciliation may need recent history |
| `users.profile.snapshot` | `compact` | n/a | Topic *is* the current view of all users |
| `loyalty.points.snapshot` | `compact,delete` | 365 days | Latest balance per user, but cap history at 1 year |
| `web.clicks` | `delete` | 24 hours | Massive volume, only used for real-time analytics |

## 1.6.6 What about a service being down longer than retention?

This is the most common operational worry. Three answers:

1. **Set retention to comfortably exceed your worst expected outage.**
   That's the primary defense.
2. **Sink everything to durable cold storage** (S3 / GCS via Kafka
   Connect). If a service needs to backfill beyond retention, it
   reads from S3, not Kafka. Kafka is a "hot tier"; S3 is the "cold
   tier."
3. **Tiered Storage** (Kafka 3.6+): Kafka itself can offload old
   segments to S3 transparently. The topic appears to have months of
   retention, but only the last few days are on broker disks. Reads
   from old offsets are slower but possible.

## 1.6.7 Visualizing retention vs. consumer position

```
   ◀──── deleted ────▶ ◀──────── still on disk ────────▶ ◀ newest ▶
                       ▲                                          ▲
            log-start offset                            high-water mark
                       │                                          │
   email-sender   ────────────────────────────────── ▶ at HWM
   sms-sender    ─────────────────────────── ▶ a bit behind
   loyalty       ─────── ▶ way behind, but still within retention
   doomed-svc    Was at this offset before going down — already deleted
                                       ▲
                          Kafka resets it to log-start
                          on restart → data lost to this consumer
```

That picture, in your head, is what 90% of Kafka operations are about.

## 1.6.8 Spring preview (don't run yet)

In Spring Boot you don't manually touch `__consumer_offsets`. Spring
Kafka's `KafkaListenerContainerFactory` handles offset commits
automatically, with strategies you choose:

```java
@Configuration
@EnableKafka
class ConsumerConfig {

    @Bean
    DefaultKafkaConsumerFactory<String, OrderPlaced> consumerFactory() {
        Map<String, Object> props = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
            ConsumerConfig.GROUP_ID_CONFIG, "loyalty-updater",
            // start from the oldest record if no offset committed yet
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            // we'll commit offsets manually for correctness
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
        );
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, OrderPlaced> kafkaListenerContainerFactory(
            DefaultKafkaConsumerFactory<String, OrderPlaced> cf) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, OrderPlaced>();
        factory.setConsumerFactory(cf);
        factory.getContainerProperties()
               .setAckMode(ContainerProperties.AckMode.MANUAL); // ack only after success
        return factory;
    }
}
```

We'll wire and run this in Module 5. The point right now is to *see*
that consumer groups, offsets, and commit semantics are first-class
in Spring Kafka — you don't have to manage `__consumer_offsets`
yourself.

## 1.6.9 Check yourself

1. Your `orders.placed` topic has `retention.ms = 86400000` (1 day).
   The loyalty service goes down Friday evening and is restored
   Monday morning. What happens to Saturday's orders from loyalty's
   point of view?
2. You add a brand-new `whatsapp-sender` consumer group on Tuesday.
   `orders.placed` has 7-day retention. How many days of past orders
   will WhatsApp see when it first starts?
3. Why would you ever choose `cleanup.policy = compact` instead of
   `delete`?
4. What's a tombstone, and why does it exist?
5. If Kafka doesn't track who consumed what, how do *you* find out
   that the loyalty service is falling behind?

Answers and the **consumer lag** metric are in Module 5. Up next in
sequence: **Module 2 — Topics, partitions, and offsets** (already on
the branch).
