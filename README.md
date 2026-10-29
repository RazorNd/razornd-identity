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

Run tests with:

```bash
./gradlew test
```

## License

This project is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
