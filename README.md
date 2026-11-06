# razornd-identity

**razornd-identity** is a reusable identity and access management service being developed as part of RazorNd's personal ecosystem of platform services. These services are intended to support prototypes and future projects.

## Requirements

- JDK 25
- Docker Compose

## Run locally

Start the supporting services:

```bash
docker compose up -d
```

Run the application with Gradle:

```bash
./gradlew bootRun
```

The Compose configuration exposes PostgreSQL at `localhost:57483` and Redis at `localhost:57484`. Spring Boot's Docker Compose integration supplies the connection details when running the application locally. PostgreSQL's local development database is `razornd_identity` (user `razornd_identity`).

## Signing keys

The authorization server loads its RSA signing key from a PKCS#12 keystore. Create a keystore and key with `keytool` (use a unique, securely stored password outside local development):

```bash
export IDENTITY_SIGNING_KEY_STORE_PASSWORD='change-this-password'

keytool -genkeypair \
  -alias identity-signing-key \
  -keyalg RSA \
  -keysize 3072 \
  -sigalg SHA256withRSA \
  -validity 3650 \
  -dname 'CN=RazorNd Identity Signing Key' \
  -storetype PKCS12 \
  -keystore identity-signing-keys.p12 \
  -storepass "$IDENTITY_SIGNING_KEY_STORE_PASSWORD" \
  -keypass "$IDENTITY_SIGNING_KEY_STORE_PASSWORD" \
  -noprompt
```

Configure the application to load it and select the alias as the active JWK `kid`:

```bash
export IDENTITY_SIGNING_KEY_LOCATION='file:./identity-signing-keys.p12'
export IDENTITY_SIGNING_KEY_ACTIVE_KEY_ID='identity-signing-key'
```

The key password defaults to the keystore password. To add another key to the same PKCS#12 file, run `keytool -genkeypair` again with a different `-alias`, the same `-keystore` path, and the same store password. Set `IDENTITY_SIGNING_KEY_ACTIVE_KEY_ID` to the alias that should sign newly issued tokens. Keep the keystore and passwords out of source control; all application replicas must use the same keystore and active key ID.

## Run the Docker image in production

The image runs only the identity service. Provide reachable PostgreSQL and Redis services, configure the properties below, and expose the application on port `8080` (or set `identity.server.port`). When running behind a reverse proxy, keep `identity.server.forward-headers-strategy` set to `framework` so the application can use forwarded host and scheme headers.

Non-secret properties can be supplied in an external `application.yml` or through environment variables using Spring Boot's relaxed binding:

| Property | Required | Description |
| --- | --- | --- |
| `identity.db.url` | Yes | JDBC URL of the application database. |
| `identity.db.username` | Yes | Database role used by the application. |
| `identity.redis.host` | Yes | Redis hostname reachable from the container. |
| `identity.redis.port` | No | Redis port; defaults to `6379`. |
| `identity.signing-key.location` | Yes | Keystore resource location, e.g. `file:/run/identity-secrets/identity-signing-keys.p12`. |
| `identity.signing-key.active-key-id` | Yes | Keystore alias used as the active JWK `kid`. |
| `identity.server.port` | No | HTTP port; defaults to `8080`. |
| `identity.server.forward-headers-strategy` | No | Forwarded-header handling; defaults to `framework`. |

The application imports `/run/secrets/` as a Spring Boot configtree. Mount secret files there with filenames equal to their property names:

| Secret filename | Purpose |
| --- | --- |
| `identity.db.password` | Database password. |
| `identity.redis.password` | Redis password; provide an empty file only if Redis is intentionally configured without authentication. |
| `identity.signing-key.store-password` | PKCS#12 keystore password. |
| `identity.signing-key.key-passwords.<key-id>` | Optional password for a specific private key; defaults to the keystore password. |
| `identity.clients.<client-id>.secret` | Client secret when the external client configuration references this alias. |

Mount the PKCS#12 file separately at the path configured by `identity.signing-key.location`. If external configuration is needed (for example, OAuth client registrations), mount it under `/etc/razornd-identity/` and set `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/etc/razornd-identity/`. Keep configuration and secret files readable by the container, but do not include secrets in the image or source control. Configure at least one OAuth client in the external application configuration before accepting client authentication or authorization requests.

Run tests with:

```bash
./gradlew test
```

## License

This project is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
