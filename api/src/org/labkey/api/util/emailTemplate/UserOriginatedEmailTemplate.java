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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Convenience base class for email templates that will be used in the context of a specific user,
 * typically the user who initiated the action that is causing the email to be sent (such as posting
 * to a message forum).
 *
 * Created by xingyang on 12/7/15.
 */
public abstract class UserOriginatedEmailTemplate extends EmailTemplate
{
    private List<ReplacementParam> _replacements = new ArrayList<>();
    protected User _originatingUser;

    public UserOriginatedEmailTemplate(@NotNull String name)
    {
        this(name, "", "", "", ContentType.Plain);
    }

    public UserOriginatedEmailTemplate(@NotNull String name, String subject, String body, String description)
    {
        this(name, subject, body, description, ContentType.Plain);
    }

    public UserOriginatedEmailTemplate(@NotNull String name, String subject, String body, String description, @NotNull ContentType contentType)
    {
        this(name, subject, body, description, contentType, DEFAULT_SENDER, DEFAULT_REPLY_TO);
    }

    public UserOriginatedEmailTemplate(@NotNull String name, String subject, String body, String description, @NotNull ContentType contentType, @Nullable String senderDisplayName, @Nullable String replyToEmail)
    {
        super(name, subject, body, description, contentType, senderDisplayName, replyToEmail);
        _replacements.add(new ReplacementParam<String>("userFirstName", String.class, "First name of the user who originated the action"){
            public String getValue(Container c) {
                return _originatingUser == null ? null : _originatingUser.getFirstName();
            }
        });
        _replacements.add(new ReplacementParam<String>("userLastName", String.class, "Last name of the user who originated the action"){
            public String getValue(Container c) {
                return _originatingUser == null ? null : _originatingUser.getLastName();
            }
        });
        _replacements.add(new ReplacementParam<String>("userDisplayName", String.class, "Display name of the user who originated the action"){
            public String getValue(Container c) {
                return _originatingUser == null ? null : _originatingUser.getFriendlyName();
            }
        });
        _replacements.add(new ReplacementParam<String>("userEmail", String.class, "Email address of the user who originated the action"){
            public String getValue(Container c) {
                return _originatingUser == null ? null : _originatingUser.getEmail();
            }
        });

        _replacements.addAll(super.getValidReplacements());
    }
    public List<ReplacementParam> getValidReplacements(){return _replacements;}
    public void setOriginatingUser(User user){_originatingUser = user;}
}
