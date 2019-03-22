/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by adam on 4/3/2016.
 */
public class TokenAuthenticationManager extends SessionKeyManager<User>
{
    private final static TokenAuthenticationManager INSTANCE = new TokenAuthenticationManager();

    public static TokenAuthenticationManager get()
    {
        return INSTANCE;
    }

    private TokenAuthenticationManager()
    {
    }

    @Override
    @NotNull
    protected String getSessionAttributeName()
    {
        return "token";
    }

    @Override
    @Nullable
    protected String getKeyPrefix()
    {
        return null;
    }

    @Override
    protected User validateContext(User user, String key)
    {
        return user;
    }
}
