/*
 * Copyright 2026 Daniil <RazorNd> Razorenov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

INSERT INTO users (username, password, enabled)
VALUES
    ('admin-password-update', '{noop}initial-password', TRUE),
    ('admin-enabled-update', '{noop}initial-password', TRUE),
    ('admin-update-both', '{noop}initial-password', TRUE),
    ('admin-disable-account', '{noop}initial-password', TRUE)
ON CONFLICT (username) DO UPDATE
SET password = EXCLUDED.password,
    enabled = EXCLUDED.enabled;

INSERT INTO authorities (username, authority)
VALUES
    ('admin-password-update', 'ROLE_USER'),
    ('admin-enabled-update', 'ROLE_USER'),
    ('admin-update-both', 'ROLE_USER'),
    ('admin-disable-account', 'ROLE_USER')
ON CONFLICT (username, authority) DO NOTHING;
