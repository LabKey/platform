/*
 * Copyright (c) 2010-2015 LabKey Corporation
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
package org.labkey.announcements;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.rules.ExpectedException;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailPref;
import org.labkey.api.notification.EmailPrefFilter;
import org.labkey.api.notification.EmailService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.MailHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Apr 22, 2010
 * Time: 11:37:19 AM
 */
public class EmailServiceImpl implements EmailService.I
{
    private static final Logger _log = Logger.getLogger(EmailService.class);

    @Override
    public void sendMessage(EmailMessage msg, User user, Container c) throws MessagingException, ConfigurationException
    {
        MailHelper.send(msg.createMessage(), user, c);
    }

    @Override
    public void sendMessage(Collection<EmailMessage> msgs, User user, Container c)
    {
        // send the email messages from a background thread
        BulkEmailer emailer = new BulkEmailer(user, c);
        for (EmailMessage msg : msgs)
            emailer.addMessage(msg);

        emailer.start();
    }

    @Override
    public EmailMessage createMessage(String from, String[] to, String subject)
    {
        return createMessage(from, to, subject, null);
    }

    @Override
    public EmailMessage createMessage(String from, String[] to, String subject, @Nullable String message)
    {
        EmailMessage msg = new EmailMessageImpl(from, to, subject);

        if (message != null)
            msg.addContent(message);

        return msg;
    }

    @Override
    public EmailMessage createMessage(String from, String[] to, String[] cc, String subject, String message)
    {
        EmailMessageImpl msg = new EmailMessageImpl(from, to, subject);

        if (message != null)
            msg.addContent(message);

        if (cc.length > 0)
            msg.setRecipients(Message.RecipientType.CC, cc);

        return msg;
    }

    @Override
    public EmailMessage createMessage(String from, String[] to, String[] cc, String subject, String message, List<File> attachments)
    {
        EmailMessage msg = createMessage(from, to, cc, subject, message);

        if(attachments != null && attachments.size() > 0)
        {
            msg.setFiles(attachments);
        }

        return msg;
    }

    @Override
    public void setEmailPref(User user, Container container, EmailPref pref, String value)
    {
        PropertyMap props = PropertyManager.getWritableProperties(user, container, EmailService.EMAIL_PREF_CATEGORY, true);
        props.put(pref.getId(), value);

        props.save();
    }

    @Override
    public String getEmailPref(User user, Container container, EmailPref pref, @Nullable EmailPref defaultPref)
    {
        String defaultValue = pref.getDefaultValue();

        if (defaultPref != null)
        {
            Map<String, String> defaultProps = PropertyManager.getProperties(container, EmailService.EMAIL_PREF_CATEGORY);
            if (defaultProps.containsKey(defaultPref.getId()))
                defaultValue = defaultProps.get(defaultPref.getId());
            else
                defaultValue = defaultPref.getDefaultValue();
        }

        Map<String, String> props = PropertyManager.getProperties(user, container, EmailService.EMAIL_PREF_CATEGORY);
        String value = defaultValue;

        if (props.containsKey(pref.getId()))
        {
            value = pref.getValue(props.get(pref.getId()), defaultValue);
        }
        return value;
    }

    @Override
    public String getEmailPref(User user, Container container, EmailPref pref)
    {
        return getEmailPref(user, container, pref, null);
    }

    @Override
    public String getDefaultEmailPref(Container container, EmailPref pref)
    {
        Map<String, String> props = PropertyManager.getProperties(container, EmailService.EMAIL_PREF_CATEGORY);
        String value = pref.getDefaultValue();

        if (props.containsKey(pref.getId()))
            value = props.get(pref.getId());

        return value;
    }

    @Override
    public void setDefaultEmailPref(Container container, EmailPref pref, String value)
    {
        PropertyMap props = PropertyManager.getWritableProperties(container, EmailService.EMAIL_PREF_CATEGORY, true);
        props.put(pref.getId(), value);

        props.save();
    }

    @Override
    public User[] getUsersWithEmailPref(Container container, EmailPrefFilter filter)
    {
        return filter.filterUsers(container);
    }

    private static class EmailMessageImpl implements EmailMessage
    {
        private String _from;
        private String _subject;
        private Map<contentType, String> _contentMap = new HashMap<>();
        private Map<String, String> _headers = new HashMap<>();
        private Map<Message.RecipientType, String[]> _recipients = new HashMap<>();
        private List<File> _files;

        public EmailMessageImpl(String from, String[] to, String subject)
        {
            _from = from;

            _recipients.put(Message.RecipientType.TO, to);
            _subject = subject;
        }

        public String getFrom()
        {
            return _from;
        }

        public String[] getTo()
        {
            if (_recipients.containsKey(Message.RecipientType.TO))
            {
                return _recipients.get(Message.RecipientType.TO);
            }
            return new String[0];
        }

        public void setRecipients(Message.RecipientType type, String[] emails)
        {
            _recipients.put(type, emails);
        }

        public String getSubject()
        {
            return _subject;
        }

        @Override
        public void setHeader(String name, String value)
        {
            _headers.put(name, value);
        }

        @Override
        public void setFiles(List<File> files)
        {
            for (File file : files)
            {
                if(!file.exists())
                    throw new IllegalArgumentException("All files must exist.");
                if(!file.isFile())
                    throw new IllegalArgumentException("All file objects must actually be files.");
            }

            _files = files;
        }

        @Override
        public void addContent(String content)
        {
            _contentMap.put(contentType.PLAIN, content);
        }

        @Override
        public void addContent(contentType type, String content)
        {
            _contentMap.put(type, content);
        }

        @Override
        public void addContent(contentType type, ViewContext context, HttpView view) throws Exception
        {
            addContent(type, context.getRequest(), view);
        }

        @Override
        public void addContent(contentType type, HttpServletRequest request, HttpView view) throws Exception
        {
            // set the frame type to none to remove the extra div that gets added otherwise.
            if (view instanceof JspView)
                ((JspView)view).setFrame(WebPartView.FrameType.NOT_HTML);

            MockHttpServletResponse response = new MockHttpServletResponse();
            HttpView.include(view, request, response);

            addContent(type, response.getContentAsString());
        }

        @Override
        public MimeMessage createMessage() throws MessagingException
        {
            MimeMessage msg = new MimeMessage(MailHelper.getSession());
            boolean multipart = _contentMap.size() > 1 || (_files != null && _files.size() > 0);
            MimeMultipart multiPartContent = null;

            if (multipart)
            {
                multiPartContent = new MimeMultipart("alternative");
                msg.setContent(multiPartContent);
            }

            msg.setFrom(new InternetAddress(_from));

            for (Map.Entry<Message.RecipientType, String[]> entry : _recipients.entrySet())
            {
                List<InternetAddress> addresses = new ArrayList<>();

                for (String email : entry.getValue())
                    addresses.add(new InternetAddress(email));

                msg.setRecipients(entry.getKey(), addresses.toArray(new InternetAddress[addresses.size()]));
            }

            if (!_headers.isEmpty())
            {
                for (Map.Entry<String, String> entry : _headers.entrySet())
                    msg.setHeader(entry.getKey(), entry.getValue());
            }

            msg.setSubject(_subject);

            if (!_contentMap.isEmpty())
            {

                for (Map.Entry<contentType, String> entry : _contentMap.entrySet())
                {
                    if (multipart && multiPartContent != null)
                    {
                        BodyPart body = new MimeBodyPart();
                        body.setContent(entry.getValue(), entry.getKey().getMimeType());

                        multiPartContent.addBodyPart(body);
                    }
                    else
                        msg.setContent(entry.getValue(), entry.getKey().getMimeType());
                }
            }

            if (_files != null && _files.size() > 0)
            {
                for(File file : _files)
                {
                    BodyPart fileBodyPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(file);
                    fileBodyPart.setDataHandler(new DataHandler(source));
                    fileBodyPart.setFileName(file.getName());
                    fileBodyPart.setDisposition(Part.ATTACHMENT);
                    multiPartContent.addBodyPart(fileBodyPart);
                }
            }

            return msg;
        }
    }

    // Sends one or more email messages in a background thread.  Add message(s) to the emailer, then call start().
    public static class BulkEmailer extends Thread
    {
        private List<EmailMessage> _messages = new ArrayList<>();
        private Container _container;
        private User _user;

        public BulkEmailer(User user, Container c)
        {
            _user = user;
            _container = c;
        }

        public void addMessage(EmailMessage msg)
        {
            _messages.add(msg);
        }

        public void run()
        {
            for (EmailMessage msg : _messages)
            {
                try {
                    Message m = msg.createMessage();
                    MailHelper.send(m, _user, _container);
                }
                catch (MessagingException e)
                {
                    _log.error("Failed to send message: " + msg.getSubject(), e);
                }
                catch (ConfigurationException ex)
                {
                    _log.error("Unable to send email.", ex);
                }
            }
        }
    }

    public static class TestCase extends Assert
    {
        @org.junit.Rule
        public ExpectedException exception = ExpectedException.none();

        private static final String PROTOCOL_ATTACHMENT_NAME = "Protocol.txt";
        private static final String NON_EXISTENT_ATTACHMENT_NAME = "fake_file.txt";
        private static final String FAKE_DIRECTORY_NAME = "/path/to/fake/directory";

        @org.junit.Test
        public void testEmailAttachments() throws MessagingException, IOException
        {
            EmailMessage msg = getBaseMessage();
            File attachment = getAttachment(PROTOCOL_ATTACHMENT_NAME);

            if(attachment == null)
                return;

            assertTrue("Couldn't find " + attachment, attachment.isFile());

            List<String> lines = Files.readAllLines(Paths.get(attachment.toURI()), Charset.defaultCharset());
            msg.setFiles(new ArrayList<>(Arrays.asList(attachment)));


            String message = convertMessageToString(msg);

            assertTrue("Message did not contain attachment with name " + attachment.getName(),
                    message.contains("Content-Disposition: attachment; filename=" + attachment.getName()));

            for(String line : lines)
            {
                assertTrue("Message did not contain entirety of attachment. Missing line: " + line,
                        message.contains(line));
            }
        }

        @org.junit.Test
        public void testNonExistentFileAttachments() throws MessagingException, IOException
        {
            EmailMessage msg = getBaseMessage();
            File attachment = getAttachment(NON_EXISTENT_ATTACHMENT_NAME);

            if (attachment == null)
                return;

            List<File> attachmentList = new ArrayList<>(Arrays.asList(attachment));

            exception.expect(IllegalArgumentException.class);
            msg.setFiles(attachmentList);
        }

        @org.junit.Test
        public void testDirectoryAttachment() throws MessagingException, IOException
        {
            EmailMessage msg = getBaseMessage();
            File attachment = getAttachment(FAKE_DIRECTORY_NAME);

            if (attachment == null)
                return;

            List<File> attachmentList = new ArrayList<>(Arrays.asList(attachment));

            exception.expect(IllegalArgumentException.class);
            msg.setFiles(attachmentList);
        }

        private EmailMessage getBaseMessage()
        {
            return EmailService.get().createMessage("test@example.com",
                    new String[]{"to@example.com"},
                    "JUnit Test Email",
                    "This is a test email.");
        }

        private File getAttachment(String fileName)
        {
            AppProps.Interface props = AppProps.getInstance();
            if (!props.isDevMode()) // We can only run the test if we're in dev mode and have access to sampledata
                return null;

            String projectRootPath = props.getProjectRoot();
            return Paths.get(projectRootPath).resolve("sampledata/study").resolve(fileName).toFile();
        }

        private String convertMessageToString(EmailMessage msg) throws MessagingException, IOException
        {
            MimeMessage mimeMsg = msg.createMessage();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            mimeMsg.writeTo(baos);

            return baos.toString();
        }
    }
}