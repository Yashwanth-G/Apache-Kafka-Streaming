# Module 1 — What is Kafka and why does it exist?

## 1.1 The problem Kafka solves

Imagine your company has 20 services: a website, a mobile app, a payments
service, a fraud-detection service, an email service, a recommendation
engine, an analytics warehouse, and so on. Every service produces events
("user signed up", "payment succeeded", "item viewed") and every other
service might need to react to those events.

If every service talks directly to every other service, you get a
**spaghetti** of point-to-point connections:

```
website ──▶ email
website ──▶ analytics
website ──▶ fraud
payments ──▶ email
payments ──▶ analytics
payments ──▶ fraud
... and so on, N × N connections
```

This breaks down because:
- Adding a new consumer means changing every producer.
- If the email service is down, the website blocks.
- Different services need data at different speeds.
- Nobody has a clear, replayable history of what happened.

**Kafka's job** is to sit in the middle as a **durable, ordered, replayable
log of events** that every service writes to and reads from independently.

```
website ──┐
payments ─┼──▶ [ KAFKA ] ──▶ email
mobile ───┘                ──▶ analytics
                           ──▶ fraud
                           ──▶ recommendations
```

Producers don't know who's reading. Consumers don't know who's writing.
They both just talk to Kafka.

## 1.2 The Post Office analogy (memorize this one)

Kafka is best understood as a **giant, multi-clerk post office** where
letters (events) are filed in numbered mailboxes:

| Real world          | Kafka term                                          |
|---------------------|-----------------------------------------------------|
| A letter            | A **message / record / event** (a key + value + headers + timestamp) |
| A mail category, e.g. "Bills"            | A **topic** (e.g. `payments.succeeded`)             |
| A single clerk's slot of mail | A **partition** (a strictly ordered queue inside a topic) |
| The post office building | A **broker** (one Kafka server)                |
| A chain of post offices | A **cluster** (multiple brokers working together) |
| The person mailing letters | A **producer**                                |
| The person picking up mail | A **consumer**                                |
| A household sharing the workload of reading the mail | A **consumer group** |
| The serial number stamped on each letter as it arrives | The **offset** |

Key property: once a letter is filed into a slot, it gets a **permanent
sequence number** (offset) and **never moves**. Readers can come back
tomorrow, next week, or next month and re-read from any offset, as long
as the post office is still keeping that mail (retention).

## 1.3 The append-only log

Inside each partition, Kafka stores records like this:

```
partition: payments.succeeded-0
offset:    0    1    2    3    4    5    6    7  ...
record:   [E0] [E1] [E2] [E3] [E4] [E5] [E6] [E7]
                                                  ▲
                                          new writes append here
```

Three crucial properties:

1. **Append-only.** New records always go to the end. Existing records
   are never modified in place. This is what makes Kafka fast — disks
   are very fast at sequential writes.
2. **Ordered within a partition.** Offset 5 was definitely written
   after offset 4 in that partition. (Across partitions, no order
   guarantee.)
3. **Durable.** Records are written to disk and replicated to other
   brokers before being acknowledged.

Analogy: a **bank ledger**. You don't erase line 47 because you made a
mistake; you write line 48 that corrects it. The history is permanent
and auditable.

## 1.4 How Kafka is different from a queue (RabbitMQ, SQS)

A traditional queue is **destructive**: once a consumer reads a message,
it's gone. If two different services need the same message, you either
fan it out into two queues, or one of them never sees it.

Kafka is a **log**, not a queue:

| Traditional queue          | Kafka log                                |
|----------------------------|------------------------------------------|
| Reading deletes the message | Reading just **advances your offset**; the message stays |
| One consumer wins the message | Every consumer group reads the full stream independently |
| Hard to replay history     | Trivial — reset your offset and read again |
| Optimized for small in-flight messages | Optimized for high-throughput streams (millions/sec) |

Analogy: a queue is a **conveyor belt** where the item falls off the end
once picked up. Kafka is a **DVR** — many people can watch the same show
from different points, and you can rewind.

## 1.5 What Kafka is *not*

Important misconceptions to clear up early:

- **It's not a database** you query. You can't say "give me the user
  with id=42". You can only read records by partition + offset (or by
  reading from the beginning).
- **It's not a message bus with routing rules** like RabbitMQ. Kafka
  doesn't decide who gets what; consumers decide.
- **It's not real-time in the millisecond-stock-trading sense** by
  default. Typical end-to-end latency is tens of milliseconds, which is
  "real-time enough" for almost everything.
- **It's not magic.** Misconfigure replication or acks and you *will*
  lose data. We'll cover this in Module 6 and 7.

## 1.6 A first concrete example

Say we have a topic `user.signups` with 3 partitions. A new user signs
up with `user_id=42`.

1. The producer service sends a record:
   - key = `"42"`
   - value = `{"user_id": 42, "email": "a@b.com", "ts": "..."}`
2. Kafka hashes the key and decides this goes to **partition 1**.
3. It appends the record at the next offset of partition 1, say
   **offset 9173**.
4. Kafka replicates it to follower brokers and acknowledges the producer.
5. The **email service** (consumer group `email-sender`) is reading
   partition 1 and is currently at offset 9172. It reads 9173, sends a
   welcome email, and advances its offset to 9174.
6. The **analytics service** (consumer group `warehouse-loader`) reads
   the same partition 1 independently, maybe an hour behind, and is at
   offset 8800. It will eventually catch up to 9173 too.

That's Kafka in one frame. The rest of the course is making this work
reliably, fast, and at scale.

## 1.7 Check yourself

Answer these before moving on:

1. Why does Kafka use an append-only log instead of letting you update
   records in place?
2. If two consumers — `email-sender` and `warehouse-loader` — both want
   to read every signup event, do they need separate topics, or can
   they share one? Why?
3. What's the guarantee Kafka makes about message order? What's it
   explicitly *not* guarantee?
4. Your teammate says "let's use Kafka as our user database, since it
   stores everything." What's wrong with that?

Answers and the next concepts (topics, partitions, offsets in depth) are
in Module 2.
