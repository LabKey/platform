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
}
