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
package org.labkey.announcements;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailPref;
import org.labkey.api.notification.EmailService;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.MailHelper.BulkEmailer;
import org.labkey.api.util.MimeMap.MimeType;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
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
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * User: klum
 * Date: Apr 22, 2010
 * Time: 11:37:19 AM
 */
public class EmailServiceImpl implements EmailService
{
    private static final Logger _log = Logger.getLogger(EmailService.class);
    private static final String EMAIL_PREF_CATEGORY = "EmailService.emailPrefs";

    @Override
    public void sendMessages(Collection<EmailMessage> msgs, User user, Container c)
    {
        // send the email messages from a background thread
        BulkEmailer emailer = new BulkEmailer(user);

        for (EmailMessage msg : msgs)
        {
            try
            {
                MimeMessage mm = msg.createMessage();
                emailer.addMessage(msg.getTo(), mm);
            }
            catch (MessagingException e)
            {
                _log.error("Failed to send message: " + msg.getSubject(), e);
            }
        }

        emailer.start();
    }

    @Override
    public EmailMessage createMessage(String from, List<String> to, String subject)
    {
        return new EmailMessageImpl(from, to, subject);
    }

    @Override
    public void setEmailPref(User user, Container container, EmailPref pref, String value)
    {
        PropertyMap props = PropertyManager.getWritableProperties(user, container, EMAIL_PREF_CATEGORY, true);
        props.put(pref.getId(), value);

        props.save();
    }

    @Override
    public String getEmailPref(User user, Container container, EmailPref pref, @Nullable EmailPref defaultPref)
    {
        String defaultValue = pref.getDefaultValue();

        if (defaultPref != null)
        {
            Map<String, String> defaultProps = PropertyManager.getProperties(container, EMAIL_PREF_CATEGORY);
            if (defaultProps.containsKey(defaultPref.getId()))
                defaultValue = defaultProps.get(defaultPref.getId());
            else
                defaultValue = defaultPref.getDefaultValue();
        }

        Map<String, String> props = PropertyManager.getProperties(user, container, EMAIL_PREF_CATEGORY);
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
        Map<String, String> props = PropertyManager.getProperties(container, EMAIL_PREF_CATEGORY);
        String value = pref.getDefaultValue();

        if (props.containsKey(pref.getId()))
            value = props.get(pref.getId());

        return value;
    }

    @Override
    public void setDefaultEmailPref(Container container, EmailPref pref, String value)
    {
        PropertyMap props = PropertyManager.getWritableProperties(container, EMAIL_PREF_CATEGORY, true);
        props.put(pref.getId(), value);

        props.save();
    }

    private static class EmailMessageImpl implements EmailMessage
    {
        private final String _from;
        private final Map<Message.RecipientType, List<String>> _recipients = new HashMap<>();
        private final String _subject;
        private final Map<MimeType, String> _contentMap = new HashMap<>();
        private final Map<String, String> _headers = new HashMap<>();

        private List<File> _files;
        private String _senderName;

        public EmailMessageImpl(String from, List<String> to, String subject)
        {
            _from = from;
            _recipients.put(Message.RecipientType.TO, to);
            _subject = subject;
        }

        public String getFrom()
        {
            return _from;
        }

        public void setSenderName(String senderName)
        {
            _senderName = senderName;
        }

        public List<String> getTo()
        {
            if (_recipients.containsKey(Message.RecipientType.TO))
            {
                return _recipients.get(Message.RecipientType.TO);
            }
            return Collections.emptyList();
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
        public void addContent(MimeType type, String content)
        {
            _contentMap.put(type, content);
        }

        @Override
        public void addContent(MimeType type, HttpServletRequest request, HttpView view) throws Exception
        {
            // set the frame type to none to remove the extra div that gets added otherwise.
            if (view instanceof JspView)
                ((JspView)view).setFrame(WebPartView.FrameType.NOT_HTML);

            MockHttpServletResponse response = new MockHttpServletResponse();
            HttpView.include(view, request, response);

            // Call trim() to avoid leading blank lines corresponding with import statements, etc in JSPs
            addContent(type, response.getContentAsString().trim());
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

            try
            {
                if (_senderName == null)
                    msg.setFrom(new InternetAddress(_from));
                else
                    msg.setFrom(new InternetAddress(_from, _senderName));
            }
            catch (UnsupportedEncodingException e)
            {
                throw new MessagingException(e.getMessage(), e);
            }

            for (Entry<Message.RecipientType, List<String>> entry : _recipients.entrySet())
            {
                List<InternetAddress> addresses = new ArrayList<>();

                for (String email : entry.getValue())
                    addresses.add(new InternetAddress(email));

                msg.setRecipients(entry.getKey(), addresses.toArray(new InternetAddress[addresses.size()]));
            }

            for (Entry<String, String> entry : _headers.entrySet())
                msg.setHeader(entry.getKey(), entry.getValue());

            msg.setSubject(_subject);

            for (Entry<MimeType, String> entry : _contentMap.entrySet())
            {
                if (multipart)
                {
                    BodyPart body = new MimeBodyPart();
                    body.setContent(entry.getValue(), entry.getKey().getContentType());

                    multiPartContent.addBodyPart(body);
                }
                else
                    msg.setContent(entry.getValue(), entry.getKey().getContentType());
            }

            if (_files != null && _files.size() > 0)
            {
                for (File file : _files)
                {
                    BodyPart fileBodyPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(file);
                    fileBodyPart.setDataHandler(new DataHandler(source));
                    fileBodyPart.setFileName(file.getName());
                    fileBodyPart.setDisposition(Part.ATTACHMENT);

                    if (null != multiPartContent)
                        multiPartContent.addBodyPart(fileBodyPart);
                }
            }

            return msg;
        }
    }

    public static class TestCase extends Assert
    {
        @Rule
        public ExpectedException exception = ExpectedException.none();

        private static final String PROTOCOL_ATTACHMENT_PATH = "study/Protocol.txt";
        private static final String NON_EXISTENT_ATTACHMENT_NAME = "fake_file.txt";
        private static final String FAKE_DIRECTORY_NAME = "/path/to/fake/directory";

        @Test
        public void testEmailAttachments() throws MessagingException, IOException
        {
            EmailMessage msg = getBaseMessage();
            File attachment = JunitUtil.getSampleData(null, PROTOCOL_ATTACHMENT_PATH);

            if (attachment == null)
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

        @Test
        public void testNonExistentFileAttachments() throws MessagingException, IOException
        {
            testAttachmentExceptions(NON_EXISTENT_ATTACHMENT_NAME);
        }

        @Test
        public void testDirectoryAttachment() throws MessagingException, IOException
        {
            testAttachmentExceptions(FAKE_DIRECTORY_NAME);
        }

        private void testAttachmentExceptions(String name) throws MessagingException, IOException
        {
            EmailMessage msg = getBaseMessage();
            File studySampleData = JunitUtil.getSampleData(null, "study");

            if (studySampleData == null)
                return;

            List<File> attachmentList = Collections.singletonList(new File(studySampleData, name));

            exception.expect(IllegalArgumentException.class);
            msg.setFiles(attachmentList);
        }

        private EmailMessage getBaseMessage()
        {
            EmailMessage msg = EmailService.get().createMessage("test@example.com",
                Collections.singletonList("to@example.com"),
                "JUnit Test Email");
            msg.addContent(MimeType.HTML, "This is a test email.");

            return msg;
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