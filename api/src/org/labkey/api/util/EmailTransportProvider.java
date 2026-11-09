/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.util;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;

import java.util.Properties;

/**
 * Interface for email transport providers (SMTP, Microsoft Graph, etc.).
 * Implementations handle configuration loading and message sending.
 */
public interface EmailTransportProvider
{
    /**
     * @return the display name of this provider for logging purposes
     */
    String getName();

    /**
     * @return a short, human-readable hint describing how to configure this provider, used when building the
     * "no email transport configured" error message. For example, {@code "SMTP (mail.smtp.*)"}. Only the hints of
     * registered providers are shown, so an undeployed provider (e.g. Microsoft Graph) never appears in the message.
     */
    String getConfigurationHint();

    /**
     * Load configuration from startup properties and/or ServletContext.
     * Called once during initialization.
     */
    void loadConfiguration();

    /**
     * @return true if this provider is fully configured and ready to send email
     */
    boolean isConfigured();

    /**
     * Send an email message using this transport.
     *
     * @param message the message to send
     * @throws MessagingException if sending fails
     */
    void send(Message message) throws MessagingException;

    /**
     * @return the {@link Session} to associate with newly created messages. The session travels with the message and
     * carries any transport-specific configuration needed at send time (e.g. SMTP host/port/auth). Transports that
     * don't rely on the session (e.g. Microsoft Graph, which reads the assembled MIME content) can use the default
     * neutral session.
     */
    default Session getSession()
    {
        return MailHelper.getDefaultSession();
    }

    /**
     * @return the configuration properties for this provider
     */
    Properties getProperties();
}
