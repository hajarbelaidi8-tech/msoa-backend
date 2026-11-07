## Payments Service (port 8085)

Dossier : `payments-service` | Base URL : `http://localhost:8085` (Docker : `http://payments:8085`)
Docs : `http://localhost:8085/docs` | OpenAPI : `payments-service/payment-openapi.json`
Statut : tous les endpoints sont implémentés et testés (11 tests automatiques + tests manuels Swagger).

### POST /api/payments
Requête : `{ "orderId": "order-demo-001", "method": "CARD" }` (`method` = `CARD` ou `CASH`, défaut `CARD`).
`orderId` est une chaîne (pas forcément un UUID). Le montant n'est pas envoyé : Payments lit `total` chez Orders.

Réponse 201 :
```json
{
  "id": "uuid",
  "orderId": "order-demo-001",
  "amount": 250.0,
  "method": "CARD",
  "status": "SUCCESS",
  "createdAt": "2026-11-06T14:00:00Z"
}
```

Erreurs, au format `{ "message": "...", "detail": "..." }` :

| Code | Message |
|---|---|
| 404 | Order not found. |
| 409 | Order is not awaiting payment. / Payment already attempted for this order. |
| 402 | Payment declined: `<raison>` |
| 401 / 403 | Not authorized to access this order. (renvoyé quand Orders refuse le token) |
| 503 | Orders service unavailable. / Orders service error. / Could not update order status. / Payment provider unavailable. |
| 422 | Body invalide (`orderId` manquant) |

### GET /api/payments/order/{orderId}
200 avec le paiement le plus récent, ou 404 « Payment not found. » s'il n'y en a pas.

### GET /api/payments
200 avec la liste des paiements.

### GET /health
`{ "status": "UP", "service": "payment" }`

Valeurs de `status` : `SUCCESS`, `FAILED`, `REFUNDED`, `PENDING`.

### Dépendance : Payments appelle Orders (`ORDERS_URL`, défaut `http://localhost:8084`)
- `GET /api/orders/{id}` doit renvoyer au minimum `{ "id", "total" (nombre), "status": "PENDING_PAYMENT" }`, et 404 si la commande n'existe pas.
- `PATCH /api/orders/{id}/status` avec `{ "status": "PAID" }`, accepté depuis `PENDING_PAYMENT`, répond 200.
- Si la requête contient `Authorization: Bearer <token>`, Payments le transmet tel quel à Orders (GET et PATCH). Sans token, aucun en-tête n'est envoyé. Un 401/403 d'Orders est renvoyé tel quel.

### Cas de test
`method` = `TEST_DECLINE` (402), `TEST_TIMEOUT` (503) ; total > 50000 refusé (402).