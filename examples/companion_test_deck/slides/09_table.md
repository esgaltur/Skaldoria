<!-- layout: table -->
## Endpoint Scopes

| Endpoint | Method | Speaker | Audience |
|---|---|---|---|
| /api/state | GET | notes included | notes withheld |
| /api/action | POST | token required | 401 |
| /api/poll/vote | POST | allowed | allowed |
| /api/qa/submit | POST | allowed | allowed |
| /api/qa/dismiss | POST | token required | 401 |

<!-- note: Try hitting /api/action from the audience phone browser. It should refuse with a 401. -->
