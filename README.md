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

The Compose configuration exposes PostgreSQL at `localhost:57483` and Redis at `localhost:57484`. PostgreSQL's local development database is `razornd_identity` (user `razornd_identity`).

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
export IDENTITY_SIGNING_KEY_ID='identity-signing-key'
```

The key password defaults to the keystore password. To add another key to the same PKCS#12 file, run `keytool -genkeypair` again with a different `-alias`, the same `-keystore` path, and the same store password. Set `IDENTITY_SIGNING_KEY_ID` to the alias that should sign newly issued tokens. Keep the keystore and passwords out of source control; all application replicas must use the same keystore and active key ID.

Run tests with:

```bash
./gradlew test
```

## License

This project is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
