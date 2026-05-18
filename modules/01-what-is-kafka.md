# Module 1 — What is Kafka and why does it exist?

## 1.1 The problem Kafka solves — a story

Forget software for a moment. Imagine you run a small **online pizza
shop**. A customer places one order on your website. To complete that
one order, *seven different things* must happen:

1. Charge the customer's credit card.
2. Send a confirmation email.
3. Send an SMS with the order number.
4. Tell the kitchen to start cooking.
5. Update the inventory ("one pepperoni pizza used up the cheese").
6. Update the live revenue dashboard for the owner.
7. Add loyalty points to the customer's account.

### The naive way: one service does it all

You write ONE function in your order service that does all 7 things,
one after another, before replying "order placed!" to the customer.

What goes wrong?
- The SMS provider is slow today → the customer waits 8 seconds for
  the page to load.
- The loyalty service is **down** → the entire order fails, even
  though the pizza could have been made.
- A new requirement comes in: "also push the order to our analytics
  warehouse." → you edit the order service again, redeploy, risk a
  bug, and now the order service has 8 responsibilities.

### The slightly-better way: services call each other directly

You break it up. The order service calls the payment service, which
calls the email service, which calls the SMS service, which calls the
kitchen service, and so on.

Now it's worse in a different way:
- Every service has to know **who** to call next. The order service
  has the phone numbers of 6 other services hardcoded.
- If the email service goes down, payment can't move forward.
- A new feature ("send a WhatsApp message too") = changing wiring in
  multiple services.
- Nobody has a **complete history** of what happened today. If the
  loyalty service had a bug between 2 and 3pm, you can't re-process
  the orders that came in during that hour.

This is called the **point-to-point** problem. It works for two or
three services. It does not work for ten.

### The Kafka way

We change the model. The order service does **just one thing**: it
writes a single piece of paper that says:

> *"At 14:03, customer #42 placed order #9981 for one pepperoni pizza,
>  $14.99, paid with card ending 4242."*

…and drops that paper into a big shared box labelled `orders.placed`.
That's it. It replies to the customer immediately. **It does not know
or care who reads that paper.**

Now, independently:
- The **email service** has someone standing at the box, reading every
  new paper, sending an email. If it's slow, it doesn't slow down
  anything else.
- The **SMS service** does the same, in parallel.
- The **kitchen display** does the same.
- The **inventory service** does the same.
- The **loyalty service** does the same.

If the **loyalty service crashes** for an hour, the papers don't
disappear. They sit in the box. When loyalty comes back up, it picks
up reading where it left off, and catches up. Nothing else was
affected.

If tomorrow someone wants to add **WhatsApp messages**, they just
write a new tiny service that also reads from the same `orders.placed`
box. **No existing service needs to change.**

That big shared box is what **Kafka** is. Your job for the rest of
this course is to understand how the box works internally so it can
be (a) durable — papers don't get lost, (b) ordered — papers per
customer stay in sequence, (c) parallel — millions of papers per
second across many readers, and (d) replayable — a new service can
catch up on the last week of papers if it wants to.

**One-line takeaway:** Kafka is the *shared, durable, append-only
notebook* that every service writes to and reads from on its own
schedule, so no service is tightly tied to any other.

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
