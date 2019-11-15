/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.ldap;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.annotations.RemoveIn20_1;
import org.labkey.api.security.ValidEmail;

import javax.naming.NamingException;
import java.util.Map;

public interface LdapAuthenticator
{
    boolean authenticate(String url, @NotNull ValidEmail email, @NotNull String password, @NotNull String principalTemplate, boolean saslAuthentication, boolean allowLdapSearch) throws NamingException;
    @RemoveIn20_1
    default void addMetrics(Map<String, Object> map){}
}
