/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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

import jakarta.mail.Address;
import jakarta.mail.Authenticator;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.MessageAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LenientStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.emailTemplate.EmailTemplate;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Provides static functions for help with sending email.
 */
public class MailHelper
{
    public static final String MESSAGE_AUDIT_EVENT = "MessageAuditEvent";

    private static final Logger _log = LogManager.getLogger(MailHelper.class);
    private static final Session DEFAULT_SESSION;

    private static Session _session = null;

    static
    {
        DEFAULT_SESSION = initDefaultSession();
        setSession(null);
    }

    public static void init()
    {
        // Invoked just to initialize DEFAULT_SESSION
    }

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
            return "One property for each JavaMail SMTP setting, documented here: https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html";
        }
    }

    private static Session initDefaultSession()
    {
        Session session = null;
        try
        {
            /* first check if specified in startup properties */
            var properties = new Properties();
            ModuleLoader.getInstance().handleStartupProperties(new LenientStartupPropertyHandler<>("mail_smtp", new SmtpStartupProperty())
            {
                @Override
                public void handle(Collection<StartupPropertyEntry> entries)
                {
                    entries.forEach(entry -> properties.put("mail.smtp." + entry.getName(), entry.getValue()));
                }
            });
            if (!properties.isEmpty())
            {
                session = Session.getInstance(properties);
            }
            else
            {
                /* check if specified in tomcat config */
                InitialContext ctx = new InitialContext();
                Context envCtx = (Context) ctx.lookup("java:comp/env");
                session = (Session) envCtx.lookup("mail/Session");
            }

            if ("true".equalsIgnoreCase(session.getProperty("mail.smtp.ssl.enable")) ||
                    "true".equalsIgnoreCase(session.getProperty("mail.smtp.starttls.enable")))
            {
                String username = session.getProperty("mail.smtp.user");
                String password = session.getProperty("mail.smtp.password");
                session = Session.getInstance(session.getProperties(), new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication()
                    {
                        return new PasswordAuthentication(username, password);
                    }
                });
            }
        }
        catch (Exception e)
        {
            _log.log(Level.ERROR, "Exception loading mail session", e);
        }

        return session;
    }

    public static void setSession(@Nullable Session session)
    {
        if (session != null)
        {
            _session = session;
        }
        else
        {
            _session = DEFAULT_SESSION;
        }
    }

    /**
     * Creates a blank email message. Caller must set all fields before sending.
     */
    public static ViewMessage createMessage()
    {
        return new ViewMessage(_session);
    }

    public static MultipartMessage createMultipartMessage()
    {
        return new MultipartMessage(_session);
    }

    /**
     * Returns the session that will be used for all messages
     */
    public static Session getSession()
    {
        return _session;
    }

    /**
     * Creates an email message, and sets the "from" and "to" fields.
     *
     * @param from Semicolon separated list of senders.
     * @param to   Semicolon separated list of recipients.
     */
    public static ViewMessage createMessage(String from, String to) throws MessagingException
    {
        return _createMessage(createMessage(), from, to);
    }

    private static ViewMessage _createMessage(ViewMessage m, String from, @Nullable String to) throws MessagingException
    {
        m.addFrom(createAddressArray(from));
        if (null != to)
            m.addRecipients(RecipientType.TO, createAddressArray(to));
        return m;
    }

    /**
     * Creates an array of email addresses from a semicolon separated list in a string.
     */
    public static Address[] createAddressArray(String s) throws AddressException
    {
        List<InternetAddress> addrs = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(s, ";");
        while (st.hasMoreTokens())
            addrs.add(new InternetAddress(st.nextToken()));

        return addrs.toArray(new Address[0]);
    }

    /**
     * Sends an email message, using the system mail session, and SMTP transport. This function logs a warning on a
     * MessagingException, and then throws it to the caller. The caller should avoid double-logging the failure, but
     * may want to handle the exception in some other way, e.g. displaying a message to the user.
     *
     * @param m    the message to send
     * @param user for auditing purposes, the user who originated the message
     * @param c    for auditing purposes, the container in which this message originated
     */
    public static void send(Message m, @Nullable User user, Container c)
    {
        try
        {
            Transport.send(m);
            addAuditEvent(user, c, m);
        }
        catch (NoSuchProviderException e)
        {
            _log.log(Level.ERROR, "Error getting SMTP transport");
        }
        catch (NumberFormatException | MessagingException e)
        {
            logMessagingException(m, e);
            throw new ConfigurationException("Error sending email: " + e.getMessage(), e);
        }
        catch (RuntimeException e)
        {
            logMessagingException(m, e);
            throw e;
        }
    }

    private static void addAuditEvent(@Nullable User user, @Nullable Container c, Message m) throws MessagingException
    {
        MessageAuditProvider.MessageAuditEvent event = new MessageAuditProvider.MessageAuditEvent(c != null ? c.getId() : ContainerManager.getRoot().getId(),
                "The Email Message: (" + m.getSubject() + ") was sent");

        try
        {
            event.setComment("The Email Message: (" + m.getSubject() + ") was sent");
            event.setFrom(getAddressStr(m.getFrom()));
            event.setTo(getAddressStr(m.getAllRecipients()));
            event.setContentType(m.getContentType());

            AuditLogService.get().addEvent(user, event);
        }
        catch (MessagingException me)
        {
            logMessagingException(m, me);
        }
    }

    private static String getAddressStr(Address[] addresses)
    {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (Address a : addresses)
        {
            sb.append(sep);
            sb.append(a.toString());

            sep = ", ";
        }
        return sb.toString();
    }

    private static final String ERROR_MESSAGE = "Exception sending email; check your SMTP configuration in " + AppProps.getInstance().getWebappConfigurationFilename();

    private static void logMessagingException(Message m, Exception e)
    {
        try
        {
            _log.error(ERROR_MESSAGE +
                "\nfrom: " + StringUtils.join(m.getFrom(), "; ") + "\n" +
                "to: " + StringUtils.join(m.getRecipients(RecipientType.TO), "; ") + "\n" +
                "subject: " + m.getSubject(), e);
        }
        catch (MessagingException ex)
        {
            //ignore
        }
    }


    public static void renderHtml(Message m, String title, Writer out)
    {
        try
        {
            Address[] from = m.getFrom();
            Address[] to = m.getRecipients(Message.RecipientType.TO);
            Address[] cc = m.getRecipients(Message.RecipientType.CC);
            Address[] bcc = m.getRecipients(Message.RecipientType.BCC);
            String subject = m.getSubject();

            String body = null;
            Object content = m.getContent();
            if (content instanceof Multipart mp)
            {
                for (int i = 0; i < mp.getCount(); i++)
                {
                    BodyPart part = mp.getBodyPart(i);
                    if ("text/html".equalsIgnoreCase(part.getContentType()))
                    {
                        body = part.getContent().toString();
                        break;
                    }
                }
                if (body == null)
                    body = mp.getBodyPart(mp.getCount() - 1).getContent().toString();
            }
            else
                body = m.getContent().toString();

            out.write("<html><head><title>" + title + "</title></head><body>\n");
            if (null != from) out.write(PageFlowUtil.filter("From: " + StringUtils.join(from, "; ")) + "<br>\n");
            if (null != to) out.write(PageFlowUtil.filter("To: " + StringUtils.join(to, "; ")) + "<br>\n");
            if (null != cc) out.write(PageFlowUtil.filter("Cc: " + StringUtils.join(cc, "; ")) + "<br>\n");
            if (null != bcc) out.write(PageFlowUtil.filter("Bcc: " + StringUtils.join(bcc, "; ")) + "<br>\n");
            if (null != subject) out.write(PageFlowUtil.filter("Subject: " + subject) + "<br><br>\n");
            if (null != body) out.write(body + "<br>\n");
            out.write("</body></html>");
        }
        catch (IOException | MessagingException e)
        {
            _log.error("renderHtml", e);
        }
    }

    // Extracts all body parts from the MimeMessage into a Map<ContentType, BodyContent>
    public static Map<String, String> getBodyParts(MimeMessage mm) throws MessagingException, IOException
    {
        Map<String, String> map = new HashMap<>();

        handleBodyParts(mm,
            (contentType, part) -> map.put(contentType, PageFlowUtil.getStreamContentsAsString(part.getInputStream())),
                map::put);

        return map;
    }

    private static void handleBodyParts(MimeMessage mm, BodyPartHandler<BodyPart> multipartHandler, BodyPartHandler<String> stringHandler) throws MessagingException, IOException
    {
        Object content = mm.getContent();

        if (content instanceof MimeMultipart multipart)
        {
            for (int i = 0; i < multipart.getCount(); i++)
            {
                BodyPart part = multipart.getBodyPart(i);
                multipartHandler.handle(StringUtils.substringBefore(part.getDataHandler().getContentType(), ";"), part);
            }
        }
        else
        {
            stringHandler.handle(StringUtils.substringBefore(mm.getContentType(), ";"), content.toString());
        }
    }

    private interface BodyPartHandler<T>
    {
        void handle(String contentType, T content) throws IOException, MessagingException;
    }

    /**
     * Message with support for a view for message body.
     */
    public static class ViewMessage extends MimeMessage
    {
        public ViewMessage(Session session)
        {
            super(session);
        }
    }

    public static class MultipartMessage extends MimeMessage
    {
        public MultipartMessage(Session session)
        {
            super(session);
        }

        private void setBodyContent(String message, String type) throws MessagingException
        {
            Object content;
            try
            {
                content = getContent();
            }
            catch (Exception e)
            {
                // will get an IOException or MessagingException if no content exists
                content = null;
            }

            if (content == null)
            {
                content = new MimeMultipart("alternative");
                setContent((Multipart) content);
            }
            BodyPart body = new MimeBodyPart();
            body.setContent(message, type);

            if (content instanceof Multipart)
                ((Multipart) content).addBodyPart(body);
        }

        public void setTextContent(String message) throws MessagingException
        {
            setBodyContent(message, "text/plain");
        }

        public void setHtmlContent(String unencodedHtml) throws MessagingException
        {
            setBodyContent(PageFlowUtil.filter(unencodedHtml, true, true), "text/html; charset=UTF-8");
        }

        public void setEncodedHtmlContent(String encodedHtml) throws MessagingException
        {
            setBodyContent(encodedHtml, "text/html; charset=UTF-8");
        }

        public void setTemplate(EmailTemplate template, Container c) throws MessagingException
        {
            String body = template.renderBody(c);
            setTextContent(body);
            setHtmlContent(body);
            setSubject(template.renderSubject(c));
        }
    }


    /**
     * Sends one or more email messages in a background thread. Add message(s) to the emailer, then call start().
     */
    public static class BulkEmailer extends Thread
    {
        private final Map<Collection<String>, MimeMessage> _messageMap = new HashMap<>(10);
        private final Map<Collection<String>, String> _containerMap = new HashMap<>(10);
        private final User _user;

        // User is for audit purposes
        public BulkEmailer(User user)
        {
            _user = user;
        }

        // Send message to multiple recipients
        public void addMessage(Collection<String> emails, MimeMessage m, @Nullable Container c)
        {
            _messageMap.put(emails, m);

            if (c != null)
                _containerMap.put(emails, c.getId());
        }

        // Send message to multiple recipients
        public void addMessage(Collection<String> emails, MimeMessage m)
        {
            addMessage(emails, m, null);
        }

        // Send message to single recipient
        public void addMessage(String email, MimeMessage m)
        {
            addMessage(Collections.singleton(email), m, null);
        }

        // Send message to single recipient
        public void addMessage(String email, MimeMessage m, Container c)
        {
            addMessage(Collections.singleton(email), m, c);
        }

        @Override
        public void run()
        {
            for (Map.Entry<Collection<String>, MimeMessage> entry : _messageMap.entrySet())
            {
                Collection<String> emails = entry.getKey();
                MimeMessage m = entry.getValue();
                String containerId = _containerMap.get(emails);
                Container c = null;

                if (containerId != null)
                    c = ContainerManager.getForId(containerId);

                for (String email : emails)
                {
                    try
                    {
                        m.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
                        MailHelper.send(m, _user, c);
                    }
                    catch (MessagingException e)
                    {
                        _log.error("Failed to send message to " + email, e);
                    }
                    catch (ConfigurationException e)
                    {
                        _log.error("Error sending email: " + e.getMessage(), e);
                    }
                }
            }
        }
    }
}
