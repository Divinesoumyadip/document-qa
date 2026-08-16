# Document Q&A backend (RAG project)

This is a backend service I built for a school office. Basically — a school
has a bunch of documents (fee policy, transport rules, HR leave rules etc) and
right now if a parent calls and asks "what's the late fee for term 2", someone
has to go open the PDF and search manually. This service lets you upload those
documents once, and then anyone can just ask a question in plain English and
get an answer, with the exact document and page number it came from.

If it doesn't know the answer, it says so instead of making something up.

## No API key needed to try it

I built it so that by default it uses a fake/local embedding system (I call it
the "stub") instead of a real AI provider. So you can literally clone this and
run it with zero setup, no OpenAI key, nothing. If you want real AI answers,
set `DOCQA_AI_PROVIDER=openai` and add your `OPENAI_API_KEY`, but that's
optional.

## How to run this (takes like 5 min)

```bash
git clone https://github.com/Divinesoumyadip/document-qa.git
cd document-qa
docker compose up --build
```

That starts the database and the app together. Once it's up, check it's alive:

```bash
curl -s localhost:8080/actuator/health
```

Then try uploading a document and asking about it:

```bash
# upload a file
curl -X POST localhost:8080/api/v1/documents \
  -H 'X-Tenant-Id: school-a' \
  -F 'file=@fee-policy.pdf' \
  -F 'title=Fee Policy 2024' \
  -F 'category=FEES'

# ask something it should know
curl -X POST localhost:8080/api/v1/chat \
  -H 'X-Tenant-Id: school-a' \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the late fee for term 2?"}'

# ask something random it has no business answering
curl -X POST localhost:8080/api/v1/chat \
  -H 'X-Tenant-Id: school-a' \
  -H 'Content-Type: application/json' \
  -d '{"question":"Who won the 2018 World Cup?"}'
```

The last one should refuse to answer, because that's not in any document.

To run the tests:

```bash
./mvnw verify
```

This needs Docker running (it spins up a real database to test against), but
still no API key.

### One annoying thing I ran into

If your Docker is a newer version (Engine 29+), it might refuse to talk to the
test library because of an API version mismatch. Fix is to tell Docker to
accept older clients:

```bash
sudo mkdir -p /etc/systemd/system/docker.service.d
printf '[Service]\nEnvironment=DOCKER_MIN_API_VERSION=1.24\n' | \
  sudo tee /etc/systemd/system/docker.service.d/api-version.conf
sudo systemctl daemon-reload && sudo systemctl restart docker
```

Took me way too long to figure this one out, so leaving it here.

## How it actually works (the two main flows)

### 1. Uploading a document

```
 You upload a file
        │
        ▼
 Check what type it actually is
 (I look at the file's actual bytes, NOT the .pdf/.docx extension —
  someone could rename a virus to fake.pdf and the extension check
  would fall for it. Reading the real bytes doesn't.)
        │
        ▼
 Save it temporarily + calculate its SHA-256 fingerprint
 (this fingerprint is how I catch duplicate uploads later)
        │
        ▼
 Save a row in the database saying "status: PROCESSING"
        │
        ▼
 Respond immediately with 202 Accepted
 (the user does NOT wait for the whole thing to finish — that
  would be slow, especially for a big PDF)
        │
        ▼
 In the background, a separate worker:
   - pulls the actual text out of the file (keeping track of
     which page each bit of text came from)
   - chops that text into small chunks (~700 characters each)
   - turns each chunk into a "vector" (numbers that represent
     the meaning of the text, so similar meanings can be found later)
   - saves all the chunks + their vectors to the database
   - flips the status to READY (or FAILED if something broke)
```

### 2. Asking a question

```
 You ask a question
        │
        ▼
 Turn the question into a vector too (same process as above)
        │
        ▼
 Search the database for chunks whose vectors are closest in
 meaning to the question's vector — but ONLY chunks that belong
 to YOUR school (tenant), never anyone else's
        │
        ▼
 Check: is the closest match actually close enough?
        │
   ┌────┴─────┐
   │           │
  NO          YES
   │           │
   ▼           ▼
 Just say   Send the matching chunks + the question to the AI
 "I don't   model, ask it to answer using ONLY those chunks,
 know" and  and return the answer along with which document/page
 stop —     it came from
 the AI
 model is
 never even
 called
```

That refusal branch matters a lot — the whole point is this shouldn't ever
make up an answer. If it doesn't have real information, it says so.

## Why I split text into chunks the way I did

School documents are usually short and structured — headings, numbered
points, sometimes a small fee table. If I just cut the text every 700
characters with no thought, I'd risk chopping a sentence right in the middle
— like separating "the late fee is Rs 500" from "for Term 2" if that split
happened to land between them. Tried that early on and it was a real
problem.

So instead I split "smartly" — try splitting by paragraph first, if a piece
is still too big, split by line, then by sentence, then by word. This way
whole sentences/paragraphs stay together as long as they physically fit,
and only get cut apart if they genuinely have to be.

I picked 700 characters per chunk with 100 characters of overlap between
chunks. 700 chars is roughly 175 words — big enough to hold a full policy
point, small enough that I can grab 5 of these chunks and still have room
left in the prompt for other stuff (like conversation history). The overlap
means if something important lands right on the edge of a chunk, it still
shows up whole in the next chunk too, so it doesn't get lost.

One small thing I did — what gets turned into a vector isn't exactly the
same as what gets shown to the user. Before converting to a vector, I stick
a small label on it like `Fee Policy [page 5, chunk 2]`. That label helps
the search actually find the right chunk when someone asks "what's the fee"
and the chunk text itself never says the word "fee policy". But when I show
the answer's source to the user, I show the clean original text — not the
label — because nobody wants to read a machine-generated tag stuck onto
their policy document.

## Which AI model + how much it costs

- Model: `text-embedding-3-small` (OpenAI's cheaper embedding model)
- Each vector has 1536 numbers in it — this is fixed once in the database,
  changing it later means redoing the whole database table
- Cost: $0.02 per million tokens (a token is roughly 3/4 of a word)

Rough math: a normal policy page is about 500 words, which is around 650
tokens. So 1000 pages ≈ 650,000 tokens ≈ **$0.013** to process the whole
thing. Less than 2 cents. That's cheap enough that I don't have to worry
about re-processing documents if I change my chunking approach later.

## The "how sure does it need to be" number (similarity threshold)

Right now this is set to 0.25 when using the free local stub, and I've put
0.65 as what it *should* be once using a real AI model (`text-embedding-3-small`).

Honest answer: I haven't fully tested 0.65 against a big real document set
yet — that's genuinely still pending. What I do know is why the number is
different between the fake local version and the real AI version: my local
stub just counts matching words, so it works on a totally different scale
than a real AI's "does this actually mean the same thing" comparison. If I
used the same number for both, either my tests would always come back empty,
or the live system would answer questions it really shouldn't.

What I actually made sure works is the mechanism itself — stuff that scores
above the number gets used, stuff below it gets refused. The exact number is
something you'd tune by testing against real questions, which I'd do next if
I had more time.

## Making sure one school can't see another school's data

Every request has to include a header saying which school it's from
(`X-Tenant-Id`). I grab that once, right at the start of the request, and
every single database query after that is forced to filter by it. There's
no path in the code where you can query the database without saying which
school you're asking for.

A few extra things I did specifically to make sure this can't be broken:

- the school ID is required in the database itself (not just checked in
  code) — so even a mistake somewhere can't accidentally save data with no
  owner
- if you try to access a document or ask about a document that isn't yours,
  you get a plain "not found" (404), not "forbidden" (403) — because
  "forbidden" would actually confirm the document exists, which leaks
  information to someone poking around
- I wrote a specific test where I upload a document as School A, then try to
  ask about it while pretending to be School B, and check that School B
  gets refused

Worth being upfront: right now the "school ID" is just a header anyone could
technically type in themselves. That's fine for this assignment, but in a
real product you'd get the school ID from a proper login/token system, not
a header someone could just set by hand.

## Some decisions I want to explain, because they're not obvious

**Why I didn't check "does this file already exist" before saving it** — I
just try to save it, and let the database reject it if it's a duplicate
(using a unique constraint on school + file fingerprint). If I checked first
and then saved, two people uploading the exact same file at the exact same
moment could both pass the check and both get saved — a race condition. The
database rejecting it is the only way to be 100% sure. And if it turns out
to be a duplicate, I don't show an error — I just return the document that's
already there, since a nervous admin double-clicking upload shouldn't get an
error message.

**Why chunk saving doesn't go through the normal database layer (JPA)** — the
normal Java database tool doesn't know how to save a "vector" type properly,
it kept trying to save it as the wrong data type. So for just this one part,
I write directly to the database with a manual SQL statement instead.

**Why I used a specific type of search index (HNSW) instead of the other
common one (IVFFlat)** — IVFFlat needs a bunch of real data already in the
table to build a good index. Since a brand new database starts empty, that
index would be useless from day one. HNSW doesn't have that problem.

**Why "file too big" errors happen before my code even runs** — I set a
size limit on the web server itself, so an oversized file gets rejected
immediately, before it's even fully received. This is better than accepting
the whole huge file into memory first and then rejecting it.

**Why I used a normal bounded task queue instead of "virtual threads"
everywhere** — for uploads, I want the system to say "I'm too busy right
now, try again shortly" if too many uploads come in at once, instead of just
accepting endless work it can never finish. A queue with a fixed size does
that naturally.

**How streaming answers can be cancelled properly** — if someone asks a
question with the "streaming" version of the API (where the answer types
itself out word by word) and then closes their browser tab, the server
notices and stops asking the AI model for more words instead of wasting
money generating an answer nobody will see.

## What I tested

Everything below runs against a real Postgres database with the vector
search extension installed (using a temporary Docker container for testing)
— not a fake in-memory database, because a fake one doesn't actually support
vector search, so testing against it wouldn't prove anything real.

- chunk splitting on weird inputs — empty file, one-word file, a file bigger
  than one chunk
- uploading a file with a wrong extension (like a program renamed to look
  like a PDF) gets rejected
- uploading the exact same file twice doesn't create two copies
- asking about School A's document while logged in as School B gets refused
- asking something with no matching document gets refused, and doesn't call
  the AI at all
- deleting a document makes the system immediately stop citing it in answers
- if the AI provider keeps failing, the system gives up gracefully after a
  few tries instead of hanging forever, and doesn't leak any internal error
  details to the user

## What's not done yet (being honest about it)

- The 0.65 threshold hasn't been tested against a real set of questions yet
- I built the code to remember previous questions in a conversation (so
  "what about class 9?" works as a follow-up), but I haven't actually
  connected it into the answer-generating step yet
- I built counters to track how many tokens/cost each request uses, but
  haven't wired them into the actual request flow yet
- Uploaded files are stored on local disk for now — fine for one server,
  but if you ran two servers behind a load balancer, this would break,
  since the second server wouldn't have the file the first one saved
- If the server crashes mid-upload, that document stays stuck on
  "processing" forever — I planned for a cleanup job to catch this but
  didn't build it yet
- No support for scanned/image-only PDFs (would need OCR, didn't have time)
- No limit on how many documents one school can upload at once — a busy
  school could theoretically slow down uploads for every other school
- Didn't attempt any of the bonus stuff (combining search with keyword
  matching, re-ranking results, etc) since the core requirements weren't
  fully polished yet

## If I had two more weeks

1. Actually test the 0.65 threshold properly — write a real list of
   questions (easy ones, tricky ones, ones it should refuse) and see where
   it starts getting things wrong
2. Finish connecting conversation memory so follow-up questions work
3. Add keyword search alongside the AI-meaning search — sometimes someone
   searches for an exact number or term ("Rs 500", "Term 2") and plain
   keyword matching would actually beat "meaning" search for that
4. Move file storage off local disk so it can run on more than one server
5. Set up automatic testing that catches it if a future change makes search
   quality worse, instead of only finding out during a demo

## The thing that surprised me most

Honestly, most of the actual difficulty had nothing to do with AI or search
at all — it was just getting the tools to cooperate. I spent way more time
figuring out why my database migrations weren't running, or why Docker
refused to talk to my testing library, than I did on the actual
search/answer logic, which ended up being pretty short.

The other thing — my first version of the "fake AI" (the one used for
testing without an API key) just converted text into random-looking numbers.
It passed all my tests, but I realized it was completely fake in a bad
way — two sentences using all the exact same words would still come out as
unrelated to each other, because it wasn't actually looking at the words
at all, just hashing them into noise. Had to rebuild it to at least count
matching words properly, otherwise my tests were basically lying to me —
they'd pass even if search was completely broken.
