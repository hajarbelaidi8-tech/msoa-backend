
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

## Auth Service (port 8081)

Dossier : `auth-service` | Base URL : `http://localhost:8081`
Stack : Django + Django REST Framework + SimpleJWT (SQLite)
Statut : tous les endpoints sont implémentés et testés (15 tests automatiques avec `python manage.py test` + tests manuels PowerShell et frontend du professeur avec `AUTH: "rest"`).

### Token
- Format : JWT (HS256), durée de vie 1 jour.
- À envoyer sur les routes protégées : `Authorization: Bearer <token>`.
- Le token contient l'identifiant de l'utilisateur (`user_id`) et la date d'expiration. Il ne contient **ni l'email ni le rôle** : pour les obtenir, appeler `GET /api/auth/me`.
- **Pour vérifier un token, appeler `GET /api/auth/me`** avec le même en-tête. Pas de clé secrète à partager entre services.

### Objet user
```json
{ "id": "user-1", "name": "Demo Customer", "email": "demo@msoa.shop", "role": "CUSTOMER" }
```
`id` est une chaîne (`user-1` pour le compte démo, `user-` suivi de 8 caractères pour les nouveaux comptes) : c'est la valeur à utiliser comme `userId` dans les commandes. `role` vaut `CUSTOMER` ou `ADMIN`. Le mot de passe n'est jamais renvoyé. L'email est stocké en minuscules.

### POST /api/auth/login
Requête : `{ "email": "demo@msoa.shop", "password": "demo123" }` (majuscules et espaces autour de l'email ignorés).
Réponse 200 :
```json
{
  "user": { "id": "user-1", "name": "Demo Customer", "email": "demo@msoa.shop", "role": "CUSTOMER" },
  "token": "<jwt>"
}
```

### POST /api/auth/register
Requête : `{ "name": "Test User", "email": "test@msoa.shop", "password": "test123" }`
Réponse 201 : l'objet user (sans token). Le client doit ensuite appeler `login`.

### POST /api/auth/logout
Token requis. Réponse 204, sans corps. Le token reste valide jusqu'à son expiration (le frontend supprime sa session).

### GET /api/auth/me
Token requis. Réponse 200 : l'objet user (directement, sans enveloppe).

### Erreurs
Format `{ "message": "...", "error": "..." }` (les deux clés ont le même texte ; le frontend lit `message`).

| Code | Message | Endpoint |
|---|---|---|
| 400 | All fields are required. | register |
| 401 | Invalid email or password. | login |
| 401 | Token absent, invalide ou expiré | logout, me |
| 409 | Email already registered. | register |

### Compte de démonstration
`demo@msoa.shop` / `demo123` (id `user-1`), créé avec `python manage.py seed_demo`.

### Appelants et dépendances
- Auth n'appelle aucun autre service.
- Orders, Payments et Shipping valident un token en appelant `GET http://localhost:8081/api/auth/me` avec l'en-tête `Authorization` reçu. Orders utilise `user.id` comme `userId` de la commande.