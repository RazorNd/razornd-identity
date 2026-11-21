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

package ru.razornd.identity.client

import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.SqlParameterValue
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import java.sql.Types
import java.util.function.Function

/** JDBC-backed registered-client repository with support for deleting clients. */
class JdbcRegisteredClientManageRepository(
    jdbcOperations: JdbcOperations,
) : JdbcRegisteredClientRepository(jdbcOperations), RegisteredClientManageRepository {

    init {
        val parametersMapper = JsonMapperRegisteredClientParametersMapper()
        registeredClientParametersMapper = Function { client ->
            val values = parametersMapper.apply(client)

            values[11] = SqlParameterValue(Types.OTHER, values[11].value)
            values[12] = SqlParameterValue(Types.OTHER, values[12].value)

            values
        }
    }

    var deleteByIdSql: String = "DELETE FROM oauth2_registered_client WHERE id = ?"

    override fun deleteById(id: String) {
        jdbcOperations.update(deleteByIdSql, id)
    }

}
