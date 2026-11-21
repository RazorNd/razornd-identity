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

CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    client_id_issued_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret VARCHAR(200),
    client_secret_expires_at TIMESTAMPTZ,
    client_name VARCHAR(200) NOT NULL,
    client_authentication_methods text NOT NULL,
    authorization_grant_types text NOT NULL,
    redirect_uris text,
    post_logout_redirect_uris text,
    scopes text NOT NULL,
    client_settings jsonb NOT NULL,
    token_settings jsonb NOT NULL,
    PRIMARY KEY (id)
);
