INSERT INTO users (username, password, enabled)
VALUES ('test-user', '{noop}test-user-passowrd', TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO authorities (username, authority)
VALUES ('test-user', 'ROLE_USER')
ON CONFLICT (username, authority) DO NOTHING;
