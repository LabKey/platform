/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
import org.labkey.api.util.ConfigurationException;

import javax.mail.MessagingException;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * User: klum
 * Date: Apr 21, 2010
 * Time: 12:04:33 PM
 */
public class EmailService
{
    public static final String EMAIL_PREF_CATEGORY = "EmailService.emailPrefs";

    static public synchronized I get()
    {
        return ServiceRegistry.get().getService(EmailService.I.class);
    }

    public interface I
    {
        EmailMessage createMessage(String from, String[] to, String subject);
        EmailMessage createMessage(String from, String[] to, String subject, String message);
        EmailMessage createMessage(String from, String[] to, String[] cc, String subject, String message);
        EmailMessage createMessage(String from, String[] to, String[] cc, String subject, String message, List<File> attachments);

        /**
         * Send the email message synchronously from the caller thread
         * @param msg message to send
         * @param user for auditing purposes, the user who is considered to have originated the message
         * @param c for auditing purposes, the container that is considered to have originated the message
         */
        void sendMessage(EmailMessage msg, User user, Container c) throws MessagingException, ConfigurationException;

        /**
         * Send the email message asynchronously in a background thread
         * @param msgs messages to send
         * @param user for auditing purposes, the user who is considered to have originated the message
         * @param c for auditing purposes, the container that is considered to have originated the message
         */
        void sendMessage(Collection<EmailMessage> msgs, User user, Container c);

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

        /**
         * Returns the array of users that match the criteria in the EmailPrefFilter. The
         * filter handles creating the initial list of users to be considered as well as
         * whether a users preference setting matches the filter criteria.
         */
        User[] getUsersWithEmailPref(Container container, EmailPrefFilter filter);
    }
}
