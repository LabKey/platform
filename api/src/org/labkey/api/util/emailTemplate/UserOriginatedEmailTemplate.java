/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.api.util.emailTemplate;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * Convenience base class for email templates that will be used in the context of a specific user,
 * typically the user who initiated the action that is causing the email to be sent (such as posting
 * to a message forum).
 *
 * Created by xingyang on 12/7/15.
 */
public abstract class UserOriginatedEmailTemplate extends EmailTemplate
{
    protected User _originatingUser;

    public UserOriginatedEmailTemplate(@NotNull String name, String description, String subject, String body, @NotNull ContentType contentType, Scope scope)
    {
        super(name, description, subject, body, contentType, scope);
    }

    @Override
    protected void addCustomReplacements(Replacements replacements)
    {
        replacements.add(new ReplacementParam<>("userFirstName", String.class, "First name of the user who originated the action")
        {
            @Override
            public String getValue(Container c)
            {
                return _originatingUser == null ? null : _originatingUser.getFirstName();
            }
        });
        replacements.add(new ReplacementParam<>("userLastName", String.class, "Last name of the user who originated the action")
        {
            @Override
            public String getValue(Container c)
            {
                return _originatingUser == null ? null : _originatingUser.getLastName();
            }
        });
        replacements.add(new ReplacementParam<>("userDisplayName", String.class, "Display name of the user who originated the action")
        {
            @Override
            public String getValue(Container c)
            {
                return _originatingUser == null ? null : _originatingUser.getFriendlyName();
            }
        });
        replacements.add(new ReplacementParam<>("userEmail", String.class, "Email address of the user who originated the action")
        {
            @Override
            public String getValue(Container c)
            {
                return _originatingUser == null ? null : _originatingUser.getEmail();
            }
        });
    }

    public void setOriginatingUser(User user){_originatingUser = user;}
}
