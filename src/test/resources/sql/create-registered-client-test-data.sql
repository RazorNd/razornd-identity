INSERT INTO oauth2_registered_client (
    id,
    client_id,
    client_id_issued_at,
    client_secret,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'fixture-id',
    'fixture-client',
    '2026-01-02T03:04:05Z',
    'fixture-secret',
    'Fixture client',
    'client_secret_basic',
    'authorization_code',
    'https://fixture.example/callback',
    'profile',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration","PT7M"],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration","PT2H"],"settings.token.authorization-code-time-to-live":["java.time.Duration","PT3M"],"settings.token.device-code-time-to-live":["java.time.Duration","PT5M"]}'
);
