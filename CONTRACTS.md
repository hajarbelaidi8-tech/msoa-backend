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


## Inventory Service (port 8083)

Dossier : `inventory-service` | Base URL : `http://localhost:8083` (Docker : `http://inventory:8083`)
Docs : `http://localhost:8083/docs`
Statut : tous les endpoints sont implémentés et testés (9 tests automatiques + tests manuels Swagger).
Les `productId` sont les mêmes que ceux du frontend et du Catalog (1 à 6). Ils sont acceptés en nombre ou en chaîne, et renvoyés en chaîne.

### GET /api/inventory
200 : `[{ "productId": "1", "quantity": 12 }]`

### GET /api/inventory/{productId}
200 : `{ "productId": "1", "quantity": 12 }`, ou 404 « Product not found. »

### POST /api/inventory/check
Requête : `{ "items": [{ "productId": "1", "quantity": 2 }] }`. Ne modifie rien.
Réponse 200 : `{ "available": true, "unavailable": [] }`

### POST /api/inventory/reserve
Même requête. **Atomique** : tous les produits sont réservés, ou aucun. Les doublons de `productId` sont fusionnés.
- 200 : `{ "reserved": true }`
- 409 : `{ "message": "Insufficient stock.", "detail": "...", "unavailable": [{ "productId": "2", "requested": 5, "available": 1 }] }` (le stock n'est pas modifié)
- 422 : body invalide, au format `{ "message": "Invalid request body.", "detail": "..." }`

### POST /api/inventory/release
Même requête. Remet le stock (annulation de commande). Réponse 200 : `{ "released": true }`

### GET /health
`{ "status": "UP", "service": "inventory" }`

### Appelant : Orders (`INVENTORY_URL`, défaut `http://localhost:8083`)
Orders appelle `reserve` à la création de la commande (un 409 signifie que la commande est refusée) et `release` à l'annulation.

## Shipping Service (port 8086)

Dossier : `shipping-service` | Base URL : `http://localhost:8086`  | Database : `shipping_db`

Statut : endpoints REST implémentés. Tests manuels Postman en cours. Intégration avec Orders à vérifier.

### GET /api/shipments

200 : retourne la liste de toutes les livraisons.

### GET /api/shipments/order/{orderId}

200 : retourne la livraison associée à la commande.

404 : aucune livraison trouvée pour cette commande.

### POST /api/shipments

Requête :

{
  "orderId": "order-demo-001",
  "address": {
    "name": "Demo Customer",
    "address": "123 Main Street",
    "city": "Casablanca"
  }
}

- `orderId` est une chaîne de caractères.
- `address.name`, `address.address` et `address.city` sont obligatoires.
- Le montant n'est pas envoyé.
- Le backend génère l'ID et le numéro de suivi (`trackingNumber`).
- Le statut initial est `PREPARING`.
- La création est autorisée uniquement si la commande existe et possède le statut `PAID`.
- Une seule livraison est autorisée par commande.

Réponse 201 : retourne les informations de la livraison créée, notamment `id`, `orderId`, `trackingNumber`, `name`, `address`, `city`, `status`, `createdAt` et `updatedAt`.

### PATCH /api/shipments/{id}/status

Requête :

{ "status": "SHIPPED" }

Statuts et transitions autorisés :

- `PREPARING` → `SHIPPED`
- `SHIPPED` → `IN_TRANSIT`
- `IN_TRANSIT` → `DELIVERED`

400 : transition de statut invalide.

404 : livraison introuvable.

### Format des erreurs

Les erreurs gérées par `GlobalExceptionHandler` utilisent le format :

{ "message": "Error description", "error": "BAD_REQUEST" }

Codes HTTP possibles selon le type d'erreur :

| Code | Cas |
|---|---|
| 400 | Données invalides ou transition de statut interdite |
| 404 | Livraison ou commande introuvable |
| 409 | Livraison déjà créée pour cette commande |
| 502 | Erreur de communication avec Orders |
| 503 | Orders Service indisponible |

### Dépendance : Shipping appelle Orders

URL actuelle : `http://localhost:8084`

- `GET /api/orders/{orderId}` permet de vérifier l'existence de la commande.
- Le champ `status` de la réponse doit être égal à `PAID`.
- Si la commande n'est pas payée, Shipping refuse la création de la livraison.
- Shipping ne modifie pas le statut de la commande et ne confirme pas le paiement.
- L'adresse de livraison est reçue dans le corps du POST `/api/shipments`.
