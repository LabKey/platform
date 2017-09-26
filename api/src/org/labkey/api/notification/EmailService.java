/*
 * Copyright (c) 2010-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.notification;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import java.util.Collection;
import java.util.List;

/**
 * User: klum
 * Date: Apr 21, 2010
 * Time: 12:04:33 PM
 */
public interface EmailService
{
    static EmailService get()
    {
        return ServiceRegistry.get().getService(EmailService.class);
    }

    EmailMessage createMessage(String from, List<String> to, String subject);

    /**
     * Sends multiple email messages asynchronously in a background thread
     * @param msgs messages to send
     * @param user for auditing purposes, the user who is considered to have originated the message
     * @param c for auditing purposes, the container that is considered to have originated the message
     */
    void sendMessages(Collection<EmailMessage> msgs, User user, Container c);

    /**
     * Returns the email configuration for the user/container combination as described by
     * the EmailPref object.
     */
    String getEmailPref(User user, Container container, EmailPref pref);

    /**
     * Returns the email configuration for the user/container combination, a separate EmailPref
     * object can be passed in to describe a configurable default preference for the container.
     */
    String getEmailPref(User user, Container container, EmailPref pref, EmailPref defaultPref);

    void setEmailPref(User user, Container container, EmailPref pref, String value);

    String getDefaultEmailPref(Container container, EmailPref pref);

    void setDefaultEmailPref(Container container, EmailPref pref, String value);
}
