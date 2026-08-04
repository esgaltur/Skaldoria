## Voting Round Trip
### Mermaid sequence diagram

```mermaid
sequenceDiagram
    participant A as Audience Phone
    participant S as Companion Server
    participant D as Projector
    A->>S: POST /api/poll/vote
    activate S
    alt first vote from this device
        S->>S: record ballot
        S-->>A: 200 OK
    else already voted
        S->>S: replace previous ballot
        S-->>A: 200 OK
    end
    deactivate S
    D->>S: GET /api/state
    S-->>D: tallies
```

<!-- note: One ballot per device. Voting again replaces your choice instead of stacking, which is why totals never inflate when someone refreshes. -->
