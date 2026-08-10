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

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.servlet.ServletContext;
import org.apache.logging.log4j.Logger;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.LenientStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Properties;

/**
 * SMTP email transport provider.
 * Loads SMTP configuration and sends email via JavaMail Transport.
 */
public class SmtpTransportProvider implements EmailTransportProvider
{
    private static final Logger LOG = LogHelper.getLogger(SmtpTransportProvider.class, "SMTP Transport Provider");

    private final Properties _properties = new Properties();
    private Session _session = null;

    private static class SmtpStartupProperty implements StartupProperty
    {
        @Override
        public String getPropertyName()
        {
            return "<JavaMail SMTP setting>";
        }

        @Override
        public String getDescription()
        {
            return "No longer supported. Configure SMTP setting in application.properties.";
        }
    }

    @Override
    public String getName()
    {
        return "SMTP";
    }

    @Override
    public void loadConfiguration()
    {
        try
        {
            // TODO: Leave in place for now to provide clear error if startup properties are present. Remove in 25.11.
            ModuleLoader.getInstance().handleStartupProperties(
                new LenientStartupPropertyHandler<>("mail_smtp", new SmtpStartupProperty())
                {
                    @Override
                    public void handle(Collection<StartupPropertyEntry> entries)
                    {
                        throw new RuntimeException("Configuring SMTP via startup properties is no longer supported. Use application.properties.");
                    }
                });

            // Load SMTP settings from ServletContext (populated from application.properties))
            ServletContext context = ModuleLoader.getServletContext();
            Enumeration<String> names = Objects.requireNonNull(context).getInitParameterNames();
            while (names.hasMoreElements())
            {
                String name = names.nextElement();
                if (name.startsWith("mail.smtp."))
                    _properties.put(name, context.getInitParameter(name));
            }

            // Create session if configured
            if (isConfigured())
            {
                _session = Session.getInstance(_properties);

                if ("true".equalsIgnoreCase(_session.getProperty("mail.smtp.ssl.enable")) ||
                        "true".equalsIgnoreCase(_session.getProperty("mail.smtp.starttls.enable")))
                {
                    String username = _session.getProperty("mail.smtp.user");
                    String password = _session.getProperty("mail.smtp.password");
                    _session = Session.getInstance(_session.getProperties(), new Authenticator()
                    {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication()
                        {
                            return new PasswordAuthentication(username, password);
                        }
                    });
                }
                LOG.info("Email configured to use SMTP transport");
            }
        }
        catch (Exception e)
        {
            LOG.error("Exception loading SMTP configuration", e);
        }
    }

    @Override
    public boolean isConfigured()
    {
        return !_properties.isEmpty() && _properties.getProperty("mail.smtp.host") != null;
    }

    @Override
    public void send(Message message) throws MessagingException
    {
        if (_session == null)
        {
            throw new MessagingException("SMTP session not initialized");
        }
        Transport.send(message);
    }

    /**
     * @return the SMTP session for creating messages, or null if not configured
     */
    public Session getSession()
    {
        return _session;
    }

    public void setSession(Session session)
    {
        _session = session;
    }

    @Override
    public Properties getProperties()
    {
        return _properties;
    }
}
