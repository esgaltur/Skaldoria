## Request Path
### Mermaid flowchart

```mermaid
flowchart LR
    Phone[Speaker Phone] -->|POST + token| Server[Companion Server]
    Server -->|authorised| Deck[Projector Deck]
    Server -->|notes| Phone
    Audience[Audience Phone] -->|vote or ask| Server
    Server -->|no notes| Audience
```

<!-- note: The two phones hit the same server but land in different scopes. Notes only ever flow back to the speaker. -->
