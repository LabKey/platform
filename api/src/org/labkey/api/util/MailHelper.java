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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.MessageAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.WebPartView;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Provides static functions for help with sending email.
 */
public class MailHelper
{
    private static Logger _log = Logger.getLogger(MailHelper.class);
    private static Session _session = null;
    public static final String MESSAGE_AUDIT_EVENT = "MessageAuditEvent";

    static
    {
        setSession(null);
    }

    public static void setSession(@Nullable Session session)
    {
        if (session != null)
        {
            _session = session;
        }
        else
        {
            try
            {
                InitialContext ctx = new InitialContext();
                Context envCtx = (Context) ctx.lookup("java:comp/env");
                _session = (Session) envCtx.lookup("mail/Session");

                if ("true".equalsIgnoreCase(_session.getProperty("mail.smtp.ssl.enable")) ||
                    "true".equalsIgnoreCase(_session.getProperty("mail.smtp.starttls.enable")))
                {
                    setSession(Session.getInstance(_session.getProperties(), new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication()
                        {
                            String username = _session.getProperty("mail.smtp.user");
                            String password = _session.getProperty("mail.smtp.password");

                            return new PasswordAuthentication(username, password);
                        }
                    }));
                }
            }
            catch (Exception e)
            {
                _log.log(Level.ERROR, "Exception loading mail session", e);
            }
        }
    }

    /**
     * Creates a blank email message.  Caller must set all fields before sending.
     */
    public static ViewMessage createMessage()
    {
        return new ViewMessage(_session);
    }

    public static ViewMessage createMultipartViewMessage()
    {
        return new ViewMessage(_session, true);
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

    /**
     * Creates an email message, and sets the "from" and "to" fields.
     *
     * @param from Semicolon separated list of senders.
     * @param to   Semicolon separated list of recipients.
     */
    public static ViewMessage createMultipartViewMessage(String from, @Nullable String to) throws MessagingException
    {
        return _createMessage(createMultipartViewMessage(), from, to);
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

        return addrs.toArray(new Address[addrs.size()]);
    }

    /**
     * Sends an email message, using the system mail session, and SMTP transport.
     * This function logs a warning on a MessagingException, and then throws it to
     * the caller.  The caller should avoid double-logging the failure, but may want
     * to handle the exception in some other way, e.g. displaying a message to the
     * user.
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
        _log.log(Level.WARN, ERROR_MESSAGE +
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
            if (content instanceof Multipart)
            {
                final Multipart mp = (Multipart) content;
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

    // Extracts just the content types from the MimeMessage body parts
    public static Set<String> getBodyPartContentTypes(MimeMessage mm) throws MessagingException, IOException
    {
        Set<String> set = new HashSet<>();

        handleBodyParts(mm,
            (contentType, part) -> set.add(contentType),
            (contentType, content) -> set.add(contentType));

        return set;
    }

    private static void handleBodyParts(MimeMessage mm, BodyPartHandler<BodyPart> multipartHandler, BodyPartHandler<String> stringHandler) throws MessagingException, IOException
    {
        Object content = mm.getContent();

        if (content instanceof MimeMultipart)
        {
            MimeMultipart multipart = (MimeMultipart)content;

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
        private final boolean _isMultipart;

        public ViewMessage(Session session, boolean isMultipart)
        {
            super(session);
            _isMultipart = isMultipart;
        }

        public ViewMessage(Session session)
        {
            this(session, false);
        }

        private void setBodyContent(HttpServletRequest request, HttpView view, String type) throws Exception
        {
            // set the frame type to none to remove the extra div that gets added otherwise.
            if (view instanceof JspView)
                ((JspView) view).setFrame(WebPartView.FrameType.NOT_HTML);

            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setCharacterEncoding("UTF-8");
            HttpView.include(view, request, response);

            if (_isMultipart)
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
                body.setContent(response.getContentAsString(), type);

                if (content instanceof Multipart)
                    ((Multipart) content).addBodyPart(body);
            }
            else
                setContent(response.getContentAsString(), type);
        }

        public void setTextContent(HttpServletRequest request, HttpView view) throws Exception
        {
            setBodyContent(request, view, "text/plain");
        }

        public void setHtmlContent(HttpServletRequest request, HttpView view) throws Exception
        {
            setBodyContent(request, view, "text/html; charset=UTF-8");
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
        private final Map<Collection<String>, MimeMessage> _map = new HashMap<>(10);
        private final User _user;

        // User is for audit purposes
        public BulkEmailer(User user)
        {
            _user = user;
        }

        // Send message to multiple recipients
        public void addMessage(Collection<String> emails, MimeMessage m)
        {
            _map.put(emails, m);
        }

        // Send message to single recipient
        public void addMessage(String email, MimeMessage m)
        {
            _map.put(Collections.singleton(email), m);
        }

        public void run()
        {
            for (Map.Entry<Collection<String>, MimeMessage> entry : _map.entrySet())
            {
                Collection<String> emails = entry.getKey();
                MimeMessage m = entry.getValue();

                for (String email : emails)
                {
                    try
                    {
                        m.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
                        MailHelper.send(m, _user, null);
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
