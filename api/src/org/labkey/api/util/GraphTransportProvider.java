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

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.models.AttachmentItem;
import com.microsoft.graph.models.AttachmentType;
import com.microsoft.graph.models.UploadSession;
import com.microsoft.graph.core.tasks.LargeFileUploadTask;
import com.microsoft.graph.core.models.UploadResult;
import com.microsoft.graph.users.UsersRequestBuilder;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import com.microsoft.graph.users.item.messages.MessagesRequestBuilder;
import com.microsoft.graph.users.item.messages.item.MessageItemRequestBuilder;
import com.microsoft.graph.users.item.messages.item.attachments.AttachmentsRequestBuilder;
import com.microsoft.graph.users.item.messages.item.attachments.createuploadsession.CreateUploadSessionPostRequestBody;
import com.microsoft.graph.users.item.messages.item.attachments.createuploadsession.CreateUploadSessionRequestBuilder;
import com.microsoft.graph.users.item.messages.item.send.SendRequestBuilder;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import com.microsoft.graph.users.item.sendmail.SendMailRequestBuilder;
import com.microsoft.kiota.RequestAdapter;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.servlet.ServletContext;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.LenientStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.logging.LogHelper;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Microsoft Graph API email transport provider.
 * Sends email via Graph API using OAuth2 client credentials.
 * Uses the Microsoft Graph SDK and Azure Identity library for authentication and API calls.
 */
public class GraphTransportProvider implements EmailTransportProvider
{
    private static final org.apache.logging.log4j.Logger LOG = LogHelper.getLogger(GraphTransportProvider.class, "Microsoft Graph Transport Provider");

    // Size threshold for using upload sessions (3MB) - smaller attachments use inline base64 encoding
    private static final int LARGE_ATTACHMENT_THRESHOLD = 3 * 1024 * 1024;

    private final Properties _properties = new Properties();

    // Lazily initialized Graph client - created on first use after configuration is loaded
    private volatile GraphServiceClient _graphClient = null;
    private final Object _clientLock = new Object();

    private static class GraphStartupProperty implements StartupProperty
    {
        @Override
        public String getPropertyName()
        {
            return "<Microsoft Graph mail setting>";
        }

        @Override
        public String getDescription()
        {
            return "Microsoft Graph email settings: tenantId, clientId, clientSecret, fromAddress";
        }
    }

    @Override
    public String getName()
    {
        return "Microsoft Graph";
    }

    @Override
    public void loadConfiguration()
    {
        try
        {
            // Load from startup properties group "mail_graph"
            ModuleLoader.getInstance().handleStartupProperties(
                new LenientStartupPropertyHandler<>("mail_graph", new GraphStartupProperty())
                {
                    @Override
                    public void handle(Collection<StartupPropertyEntry> entries)
                    {
                        entries.forEach(entry ->
                            _properties.put("mail.graph." + entry.getName(), entry.getValue()));
                    }
                });

            // Fallback: check ServletContext for Graph settings
            if (_properties.isEmpty())
            {
                LOG.debug("No startup properties found, checking ServletContext for Graph settings");
                ServletContext context = ModuleLoader.getServletContext();
                if (context != null)
                {
                    Enumeration<String> names = context.getInitParameterNames();
                    while (names.hasMoreElements())
                    {
                        String name = names.nextElement();
                        if (name.startsWith("mail.graph."))
                            _properties.put(name, context.getInitParameter(name));
                    }
                }
            }

            if (isConfigured())
            {
                LOG.info("Email configured to use Microsoft Graph transport");
            }
            else
            {
                LOG.debug("Microsoft Graph transport not configured (missing required properties)");
            }
        }
        catch (Exception e)
        {
            LOG.error("Exception loading Microsoft Graph configuration", e);
        }
    }

    @Override
    public boolean isConfigured()
    {
        return StringUtils.isNotBlank(getTenantId())
                && StringUtils.isNotBlank(getClientId())
                && StringUtils.isNotBlank(getClientSecret())
                && StringUtils.isNotBlank(getFromAddress());
    }

    @Override
    public void send(Message message) throws MessagingException
    {
        if (!(message instanceof MimeMessage mm))
        {
            throw new MessagingException("Message not a MimeMessage instance");
        }
        LOG.debug("Sending email via Microsoft Graph from {}", getFromAddress());
        try
        {
            sendMessage(mm);
        }
        catch (IOException e)
        {
            throw new MessagingException("Failed sending mail via Microsoft Graph", e);
        }
    }

    /**
     * Get or create the GraphServiceClient.
     * The client is lazily initialized and reused - Azure Identity handles token caching internally.
     */
    private GraphServiceClient getGraphClient()
    {
        if (_graphClient == null)
        {
            synchronized (_clientLock)
            {
                if (_graphClient == null)
                {
                    LOG.debug("Creating GraphServiceClient for tenant {}", getTenantId());
                    _graphClient = createGraphServiceClient();
                }
            }
        }
        return _graphClient;
    }

    /**
     * Send email via Graph API using the SDK.
     * For messages with large attachments, uses draft + upload session approach.
     * For messages without attachments or with small attachments, uses direct sendMail.
     */
    private void sendMessage(MimeMessage mm) throws IOException, MessagingException
    {
        List<AttachmentInfo> attachments = extractAttachments(mm);

        // Check if any attachment exceeds the threshold for inline encoding
        boolean hasLargeAttachments = attachments.stream()
                .anyMatch(a -> a.content().length > LARGE_ATTACHMENT_THRESHOLD);

        if (hasLargeAttachments)
        {
            // Has large attachments - create draft, upload via upload sessions, then send
            sendWithLargeAttachments(mm, attachments);
        }
        else
        {
            // No attachments or small attachments - send directly via sendMail
            sendViaSdk(mm, attachments);
        }
    }

    /**
     * Holds attachment metadata and content.
     * @param contentId The Content-ID for inline attachments (without angle brackets), or null for regular attachments
     */
    private record AttachmentInfo(String name, String contentType, byte[] content, String contentId) {}

    /**
     * Extract attachments from a MimeMessage.
     */
    private List<AttachmentInfo> extractAttachments(MimeMessage mm) throws IOException, MessagingException
    {
        List<AttachmentInfo> attachments = new ArrayList<>();
        Object content = mm.getContent();

        if (content instanceof Multipart multipart)
        {
            extractAttachmentsFromMultipart(multipart, attachments);
        }

        return attachments;
    }

    /**
     * Recursively extract attachments from a Multipart.
     */
    private void extractAttachmentsFromMultipart(Multipart multipart, List<AttachmentInfo> attachments)
            throws IOException, MessagingException
    {
        for (int i = 0; i < multipart.getCount(); i++)
        {
            BodyPart part = multipart.getBodyPart(i);
            String disposition = part.getDisposition();
            String fileName = part.getFileName();

            // Check for nested multipart
            if (part.getContent() instanceof Multipart nestedMultipart)
            {
                extractAttachmentsFromMultipart(nestedMultipart, attachments);
            }
            // Parts with ATTACHMENT disposition or INLINE with filename are attachments
            else if (Part.ATTACHMENT.equalsIgnoreCase(disposition) ||
                    (Part.INLINE.equalsIgnoreCase(disposition) && fileName != null))
            {
                byte[] contentBytes = readPartContent(part);
                String contentType = part.getContentType();
                // Content-Type may include parameters like charset; extract just the MIME type
                if (contentType != null && contentType.contains(";"))
                {
                    contentType = contentType.substring(0, contentType.indexOf(";")).trim();
                }
                // Extract Content-ID for inline attachments (used for cid: references in HTML)
                String contentId = null;
                if (part instanceof MimeBodyPart mimeBodyPart)
                {
                    contentId = mimeBodyPart.getContentID();
                    // Remove angle brackets if present (e.g., "<image001>" -> "image001")
                    if (contentId != null && contentId.startsWith("<") && contentId.endsWith(">"))
                    {
                        contentId = contentId.substring(1, contentId.length() - 1);
                    }
                }
                attachments.add(new AttachmentInfo(fileName, contentType, contentBytes, contentId));
                LOG.debug("Extracted attachment: {} ({} bytes, type: {}, contentId: {})", fileName, contentBytes.length, contentType, contentId);
            }
        }
    }

    /**
     * Read content bytes from a BodyPart.
     */
    private byte[] readPartContent(BodyPart part) throws IOException, MessagingException
    {
        try (InputStream is = part.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1)
            {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }

    /**
     * Send email using the Graph SDK with inline attachments (for messages without attachments
     * or with attachments smaller than 3MB).
     */
    private void sendViaSdk(MimeMessage mm, List<AttachmentInfo> attachments)
            throws IOException, MessagingException
    {
        GraphServiceClient client = getGraphClient();
        String fromAddress = getFromAddress();

        // Build the Graph Message object
        com.microsoft.graph.models.Message graphMessage = buildGraphMessage(mm);

        // Add small attachments inline (base64 encoded)
        if (!attachments.isEmpty())
        {
            List<Attachment> graphAttachments = new ArrayList<>();
            for (AttachmentInfo attachment : attachments)
            {
                FileAttachment fileAttachment = getFileAttachment(attachment);

                graphAttachments.add(fileAttachment);
            }
            graphMessage.setAttachments(graphAttachments);
        }

        // Send via SDK
        SendMailPostRequestBody sendMailRequest = new SendMailPostRequestBody();
        sendMailRequest.setMessage(graphMessage);
        sendMailRequest.setSaveToSentItems(false);

        LOG.debug("Sending email via Graph SDK sendMail endpoint");
        client.users().byUserId(fromAddress).sendMail().post(sendMailRequest);

        LOG.debug("Email sent successfully via Microsoft Graph SDK");
    }

    private static @NotNull FileAttachment getFileAttachment(AttachmentInfo attachment)
    {
        FileAttachment fileAttachment = new FileAttachment();
        fileAttachment.setOdataType("#microsoft.graph.fileAttachment");
        fileAttachment.setName(attachment.name());
        fileAttachment.setContentType(attachment.contentType());
        fileAttachment.setContentBytes(attachment.content());

        // Set inline properties for inline attachments
        if (attachment.contentId() != null)
        {
            fileAttachment.setIsInline(true);
            fileAttachment.setContentId(attachment.contentId());
        }
        return fileAttachment;
    }

    /**
     * Send email with large attachments: create draft, upload via upload sessions, then send.
     * This approach is required for attachments over 3MB as Graph API doesn't support
     * inline base64 encoding for large files.
     */
    private void sendWithLargeAttachments(MimeMessage mm, List<AttachmentInfo> attachments)
            throws IOException, MessagingException
    {
        GraphServiceClient client = getGraphClient();
        String fromAddress = getFromAddress();

        // Step 1: Create draft message
        com.microsoft.graph.models.Message graphMessage = buildGraphMessage(mm);
        com.microsoft.graph.models.Message draft = client.users().byUserId(fromAddress)
                .messages()
                .post(graphMessage);

        if (draft == null || draft.getId() == null)
        {
            throw new IOException("Failed to create draft message");
        }

        String messageId = draft.getId();
        LOG.debug("Created draft message with ID: {}", messageId);

        try
        {
            // Step 2: Upload each attachment
            for (AttachmentInfo attachment : attachments)
            {
                if (attachment.content().length > LARGE_ATTACHMENT_THRESHOLD)
                {
                    // Large attachment - use upload session
                    uploadLargeAttachment(client, fromAddress, messageId, attachment);
                }
                else
                {
                    // Small attachment - add directly
                    addSmallAttachment(client, fromAddress, messageId, attachment);
                }
            }

            // Step 3: Send the draft
            client.users().byUserId(fromAddress)
                    .messages()
                    .byMessageId(messageId)
                    .send()
                    .post();

            LOG.debug("Email with {} attachment(s) sent successfully via Microsoft Graph SDK", attachments.size());
        }
        catch (Exception e)
        {
            // Clean up: delete draft on failure
            try
            {
                client.users().byUserId(fromAddress)
                        .messages()
                        .byMessageId(messageId)
                        .delete();
            }
            catch (Exception deleteEx)
            {
                LOG.warn("Failed to delete draft message after send failure", deleteEx);
            }
            throw new IOException("Failed to send email with attachments", e);
        }
    }

    /**
     * Build a Graph Message object from a MimeMessage.
     */
    private com.microsoft.graph.models.Message buildGraphMessage(MimeMessage mm)
            throws MessagingException, IOException
    {
        com.microsoft.graph.models.Message message = new com.microsoft.graph.models.Message();

        // Subject
        message.setSubject(mm.getSubject());

        // Body
        ItemBody body = new ItemBody();
        String[] bodyContent = extractBodyContent(mm);
        if (bodyContent[0] != null)
        {
            // HTML content
            body.setContentType(BodyType.Html);
            body.setContent(bodyContent[0]);
        }
        else if (bodyContent[1] != null)
        {
            // Text content
            body.setContentType(BodyType.Text);
            body.setContent(bodyContent[1]);
        }
        else
        {
            body.setContentType(BodyType.Text);
            body.setContent("");
        }
        message.setBody(body);

        // Recipients
        Address[] toAddresses = mm.getRecipients(Message.RecipientType.TO);
        if (toAddresses != null && toAddresses.length > 0)
        {
            List<Recipient> recipients = new ArrayList<>();
            for (Address address : toAddresses)
            {
                Recipient recipient = new Recipient();
                EmailAddress emailAddress = new EmailAddress();

                if (address instanceof InternetAddress internetAddress)
                {
                    emailAddress.setAddress(internetAddress.getAddress());
                    emailAddress.setName(internetAddress.getPersonal());
                }
                else
                {
                    emailAddress.setAddress(address.toString());
                }

                recipient.setEmailAddress(emailAddress);
                recipients.add(recipient);
            }
            message.setToRecipients(recipients);
        }

        return message;
    }

    /**
     * Extract body content from MimeMessage, handling nested multipart structures.
     * Returns String[2] where [0] is HTML content and [1] is text content.
     * <p>
     * This extraction is necessary because MimeMessage.getContent() returns either a String
     * (for simple messages) or a Multipart object (for multipart messages). The Graph API
     * expects just the HTML or plain text body content, not a MIME structure. Calling toString()
     * on a Multipart object returns something like "javax.mail.internet.MimeMultipart@1a2b3c4d",
     * not the actual content. We must traverse the multipart structure to find the body parts.
     */
    private String[] extractBodyContent(MimeMessage mm) throws MessagingException, IOException
    {
        String[] bodyContent = new String[2]; // [0] = html, [1] = text

        Object content = mm.getContent();
        if (content instanceof Multipart multipart)
        {
            extractBodyFromMultipart(multipart, bodyContent);
        }
        else if (content instanceof String str)
        {
            String contentType = mm.getContentType();
            if (contentType != null && contentType.toLowerCase().contains("text/html"))
            {
                bodyContent[0] = str;
            }
            else
            {
                bodyContent[1] = str;
            }
        }

        return bodyContent;
    }

    /**
     * Recursively extract body content from a Multipart, handling nested structures.
     */
    private void extractBodyFromMultipart(Multipart multipart, String[] bodyContent)
            throws MessagingException, IOException
    {
        LOG.debug("Processing multipart with {} parts, content type: {}", multipart.getCount(), multipart.getContentType());

        for (int i = 0; i < multipart.getCount(); i++)
        {
            BodyPart part = multipart.getBodyPart(i);
            String disposition = part.getDisposition();
            String contentType = part.getContentType();

            LOG.debug("Part {}: disposition={}, contentType={}", i, disposition, contentType);

            // Skip attachments
            if (Part.ATTACHMENT.equalsIgnoreCase(disposition))
            {
                continue;
            }

            Object partContent = part.getContent();
            LOG.debug("Part {} content class: {}", i, partContent != null ? partContent.getClass().getName() : "null");

            if (partContent instanceof Multipart nestedMultipart)
            {
                extractBodyFromMultipart(nestedMultipart, bodyContent);
            }
            else if (partContent instanceof String str)
            {
                // Check content type header first, but also detect HTML by content since
                // MimeBodyPart.setContent(obj, type) doesn't always set the Content-Type header properly
                boolean isHtml = (contentType != null && contentType.toLowerCase().contains("text/html"))
                        || containsHtmlTags(str);

                if (isHtml)
                {
                    LOG.debug("Found HTML body: {} chars", str.length());
                    bodyContent[0] = str;
                }
                else
                {
                    LOG.debug("Found text body: {} chars", str.length());
                    bodyContent[1] = str;
                }
            }
        }
    }

    // Pattern to detect common HTML tags
    private static final java.util.regex.Pattern HTML_TAG_PATTERN =
            java.util.regex.Pattern.compile("<(br|p|div|table|a|span|img|b|i|strong|em|html|head|body|ul|ol|li|h[1-6])[\\s>/]",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Detect if content contains HTML tags.
     * Needed because MimeBodyPart.setContent(obj, "text/html") doesn't always set the Content-Type header correctly.
     */
    private boolean containsHtmlTags(String content)
    {
        return content != null && HTML_TAG_PATTERN.matcher(content).find();
    }

    /**
     * Add a small attachment directly to a draft message.
     */
    private void addSmallAttachment(GraphServiceClient client, String fromAddress, String messageId,
                                    AttachmentInfo attachment)
    {
        FileAttachment fileAttachment = getFileAttachment(attachment);

        client.users().byUserId(fromAddress)
                .messages()
                .byMessageId(messageId)
                .attachments()
                .post(fileAttachment);

        LOG.debug("Added small attachment '{}' ({} bytes)", attachment.name(), attachment.content().length);
    }

    /**
     * Upload a large attachment using the SDK's LargeFileUploadTask.
     * Graph API requires upload sessions for attachments over 3MB.
     */
    private void uploadLargeAttachment(GraphServiceClient client, String fromAddress, String messageId,
                                       AttachmentInfo attachment) throws IOException
    {
        // Create attachment item
        AttachmentItem attachmentItem = getAttachmentItem(attachment);

        // Create an upload session
        CreateUploadSessionPostRequestBody uploadSessionRequest = new CreateUploadSessionPostRequestBody();
        uploadSessionRequest.setAttachmentItem(attachmentItem);

        UploadSession uploadSession = client.users().byUserId(fromAddress)
                .messages()
                .byMessageId(messageId)
                .attachments()
                .createUploadSession()
                .post(uploadSessionRequest);

        if (uploadSession == null)
        {
            throw new IOException("Failed to create upload session for attachment: " + attachment.name());
        }

        LOG.debug("Created upload session for attachment '{}'", attachment.name());

        // Use SDK's LargeFileUploadTask for chunked upload with automatic retry
        try (InputStream stream = new ByteArrayInputStream(attachment.content()))
        {
            LargeFileUploadTask<FileAttachment> uploadTask = new LargeFileUploadTask<>(
                    client.getRequestAdapter(),
                    uploadSession,
                    stream,
                    attachment.content().length,
                    FileAttachment::createFromDiscriminatorValue);

            UploadResult<FileAttachment> result = uploadTask.upload(5, null);
            if (!result.isUploadSuccessful())
            {
                throw new IOException("Failed to upload large attachment: " + attachment.name());
            }

            LOG.debug("Large attachment '{}' uploaded successfully ({} bytes)", attachment.name(), attachment.content().length);
        }
        catch (Exception e)
        {
            if (e instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
            }
            LOG.error("Failed to upload large attachment '{}' ({} bytes): {}",
                    attachment.name(), attachment.content().length, e.getMessage());
            throw new IOException("Failed to upload large attachment: " + attachment.name(), e);
        }
    }

    private static @NotNull AttachmentItem getAttachmentItem(AttachmentInfo attachment)
    {
        AttachmentItem attachmentItem = new AttachmentItem();
        attachmentItem.setAttachmentType(AttachmentType.File);
        attachmentItem.setName(attachment.name());
        attachmentItem.setSize((long) attachment.content().length);
        if (attachment.contentType() != null)
        {
            attachmentItem.setContentType(attachment.contentType());
        }
        if (attachment.contentId() != null)
        {
            attachmentItem.setIsInline(true);
            attachmentItem.setContentId(attachment.contentId());
        }
        return attachmentItem;
    }

    private String getTenantId()
    {
        return _properties.getProperty("mail.graph.tenantId");
    }

    private String getClientId()
    {
        return _properties.getProperty("mail.graph.clientId");
    }

    private String getClientSecret()
    {
        return _properties.getProperty("mail.graph.clientSecret");
    }

    private String getFromAddress()
    {
        return _properties.getProperty("mail.graph.fromAddress");
    }

    /**
     * Creates the GraphServiceClient. Override in tests to provide a mock client.
     */
    protected GraphServiceClient createGraphServiceClient()
    {
        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .tenantId(getTenantId())
                .clientId(getClientId())
                .clientSecret(getClientSecret())
                .build();

        return new GraphServiceClient(credential, getScopes());
    }

    /**
     * Returns the OAuth2 scopes to use. Override in tests if needed.
     */
    protected String[] getScopes()
    {
        return new String[]{"https://graph.microsoft.com/.default"};
    }

    @Override
    public Properties getProperties()
    {
        return _properties;
    }

    /**
     * Unit tests for GraphTransportProvider using Mockito to mock the Microsoft Graph SDK.
     * Tests verify that the provider correctly converts MimeMessages to Graph API calls.
     * These tests run without actual Graph credentials since they use mocks.
     */
    public static class TestCase extends Assert
    {
        private static final String TEST_TENANT_ID = "test-tenant-id";
        private static final String TEST_CLIENT_ID = "test-client-id";
        private static final String TEST_CLIENT_SECRET = "test-client-secret";
        private static final String TEST_FROM_ADDRESS = "sender@example.com";
        private static final String TEST_TO_ADDRESS = "recipient@example.com";
        private static final String TEST_MESSAGE_ID = "AAMkAGI1234567890";
        private static final String TEST_UPLOAD_URL = "https://graph.microsoft.com/upload-session-12345";

        private GraphServiceClient mockGraphClient;
        private RequestAdapter mockRequestAdapter;
        private UsersRequestBuilder mockUsersRequestBuilder;
        private UserItemRequestBuilder mockUserItemRequestBuilder;
        private SendMailRequestBuilder mockSendMailRequestBuilder;
        private MessagesRequestBuilder mockMessagesRequestBuilder;
        private MessageItemRequestBuilder mockMessageItemRequestBuilder;
        private AttachmentsRequestBuilder mockAttachmentsRequestBuilder;
        private CreateUploadSessionRequestBuilder mockCreateUploadSessionRequestBuilder;
        private SendRequestBuilder mockSendRequestBuilder;

        @Before
        public void setUp()
        {
            // Create mocks
            mockGraphClient = mock(GraphServiceClient.class);
            mockRequestAdapter = mock(RequestAdapter.class);
            mockUsersRequestBuilder = mock(UsersRequestBuilder.class);
            mockUserItemRequestBuilder = mock(UserItemRequestBuilder.class);
            mockSendMailRequestBuilder = mock(SendMailRequestBuilder.class);
            mockMessagesRequestBuilder = mock(MessagesRequestBuilder.class);
            mockMessageItemRequestBuilder = mock(MessageItemRequestBuilder.class);
            mockAttachmentsRequestBuilder = mock(AttachmentsRequestBuilder.class);
            mockCreateUploadSessionRequestBuilder = mock(CreateUploadSessionRequestBuilder.class);
            mockSendRequestBuilder = mock(SendRequestBuilder.class);

            // Wire up the mock chain
            when(mockGraphClient.getRequestAdapter()).thenReturn(mockRequestAdapter);
            when(mockGraphClient.users()).thenReturn(mockUsersRequestBuilder);
            when(mockUsersRequestBuilder.byUserId(anyString())).thenReturn(mockUserItemRequestBuilder);
            when(mockUserItemRequestBuilder.sendMail()).thenReturn(mockSendMailRequestBuilder);
            when(mockUserItemRequestBuilder.messages()).thenReturn(mockMessagesRequestBuilder);
            when(mockMessagesRequestBuilder.byMessageId(anyString())).thenReturn(mockMessageItemRequestBuilder);
            when(mockMessageItemRequestBuilder.attachments()).thenReturn(mockAttachmentsRequestBuilder);
            when(mockMessageItemRequestBuilder.send()).thenReturn(mockSendRequestBuilder);
            when(mockAttachmentsRequestBuilder.createUploadSession()).thenReturn(mockCreateUploadSessionRequestBuilder);
        }

        @Test
        public void testSuccessfulEmailSend() throws Exception
        {
            doNothing().when(mockSendMailRequestBuilder).post(any(SendMailPostRequestBody.class));

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessage());

            ArgumentCaptor<SendMailPostRequestBody> captor = ArgumentCaptor.forClass(SendMailPostRequestBody.class);
            verify(mockSendMailRequestBuilder).post(captor.capture());

            SendMailPostRequestBody requestBody = captor.getValue();
            assertNotNull("Request body should not be null", requestBody);
            assertNotNull("Message should not be null", requestBody.getMessage());
            assertEquals("Test email from GraphTransportProviderTest", requestBody.getMessage().getSubject());
            assertFalse("Should not save to sent items", requestBody.getSaveToSentItems());
        }

        @Test
        public void testEmailWithRecipients() throws Exception
        {
            doNothing().when(mockSendMailRequestBuilder).post(any(SendMailPostRequestBody.class));

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessage());

            ArgumentCaptor<SendMailPostRequestBody> captor = ArgumentCaptor.forClass(SendMailPostRequestBody.class);
            verify(mockSendMailRequestBuilder).post(captor.capture());

            com.microsoft.graph.models.Message message = captor.getValue().getMessage();
            assertNotNull("To recipients should not be null", message.getToRecipients());
            assertEquals("Should have one recipient", 1, message.getToRecipients().size());
            assertEquals(TEST_TO_ADDRESS, message.getToRecipients().get(0).getEmailAddress().getAddress());
        }

        @Test
        public void testSuccessfulEmailWithSmallAttachment() throws Exception
        {
            doNothing().when(mockSendMailRequestBuilder).post(any(SendMailPostRequestBody.class));

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessageWithAttachment());

            ArgumentCaptor<SendMailPostRequestBody> captor = ArgumentCaptor.forClass(SendMailPostRequestBody.class);
            verify(mockSendMailRequestBuilder).post(captor.capture());

            com.microsoft.graph.models.Message message = captor.getValue().getMessage();
            assertNotNull("Attachments should not be null", message.getAttachments());
            assertEquals("Should have one attachment", 1, message.getAttachments().size());
            assertTrue("Attachment should be a FileAttachment", message.getAttachments().get(0) instanceof FileAttachment);

            FileAttachment attachment = (FileAttachment) message.getAttachments().get(0);
            assertEquals("test.txt", attachment.getName());
            assertNotNull("Content bytes should not be null", attachment.getContentBytes());
        }

        @Test
        public void testSuccessfulEmailWithLargeAttachment() throws Exception
        {
            // For large attachments (>3MB), creates draft, uploads via LargeFileUploadTask, then sends

            // Mock draft creation
            com.microsoft.graph.models.Message draftMessage = new com.microsoft.graph.models.Message();
            draftMessage.setId(TEST_MESSAGE_ID);
            when(mockMessagesRequestBuilder.post(any(com.microsoft.graph.models.Message.class))).thenReturn(draftMessage);

            // Mock upload session creation
            UploadSession uploadSession = new UploadSession();
            uploadSession.setUploadUrl(TEST_UPLOAD_URL);
            when(mockCreateUploadSessionRequestBuilder.post(any(CreateUploadSessionPostRequestBody.class)))
                    .thenReturn(uploadSession);

            // Mock RequestAdapter for LargeFileUploadTask chunked uploads
            when(mockRequestAdapter.sendPrimitive(any(), any(), any())).thenReturn(null);

            // Mock send
            doNothing().when(mockSendRequestBuilder).post();

            // Mock draft deletion (for cleanup on failure)
            doNothing().when(mockMessageItemRequestBuilder).delete();

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessageWithLargeAttachment());

            // Verify draft was created
            verify(mockMessagesRequestBuilder).post(any(com.microsoft.graph.models.Message.class));

            // Verify upload session was created
            verify(mockCreateUploadSessionRequestBuilder).post(any(CreateUploadSessionPostRequestBody.class));

            // Verify draft was sent
            verify(mockSendRequestBuilder).post();

            // Verify sendMail was NOT called (used draft workflow instead)
            verify(mockSendMailRequestBuilder, never()).post(any(SendMailPostRequestBody.class));
        }

        @Test
        public void testLargeAttachmentUploadFailureDeletesDraft() throws Exception
        {
            // Mock draft creation
            com.microsoft.graph.models.Message draftMessage = new com.microsoft.graph.models.Message();
            draftMessage.setId(TEST_MESSAGE_ID);
            when(mockMessagesRequestBuilder.post(any(com.microsoft.graph.models.Message.class))).thenReturn(draftMessage);

            // Mock upload session creation to return null (simulating failure)
            when(mockCreateUploadSessionRequestBuilder.post(any(CreateUploadSessionPostRequestBody.class)))
                    .thenReturn(null);

            // Mock draft deletion
            doNothing().when(mockMessageItemRequestBuilder).delete();

            GraphTransportProvider provider = createTestProvider();

            try
            {
                provider.send(createTestMessageWithLargeAttachment());
                fail("Expected MessagingException due to upload session failure");
            }
            catch (MessagingException e)
            {
                // Expected
            }

            // Verify draft was created
            verify(mockMessagesRequestBuilder).post(any(com.microsoft.graph.models.Message.class));

            // Verify draft was deleted after failure
            verify(mockMessageItemRequestBuilder).delete();
        }

        @Test
        public void testEmailWithInlineAttachment() throws Exception
        {
            doNothing().when(mockSendMailRequestBuilder).post(any(SendMailPostRequestBody.class));

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessageWithInlineAttachment());

            ArgumentCaptor<SendMailPostRequestBody> captor = ArgumentCaptor.forClass(SendMailPostRequestBody.class);
            verify(mockSendMailRequestBuilder).post(captor.capture());

            com.microsoft.graph.models.Message message = captor.getValue().getMessage();
            assertNotNull("Attachments should not be null", message.getAttachments());
            assertEquals("Should have one attachment", 1, message.getAttachments().size());

            FileAttachment attachment = (FileAttachment) message.getAttachments().get(0);
            assertTrue("Attachment should be marked as inline", attachment.getIsInline());
            assertEquals("image001", attachment.getContentId());
        }

        @Test
        public void testEmailWithMultipleAttachments() throws Exception
        {
            doNothing().when(mockSendMailRequestBuilder).post(any(SendMailPostRequestBody.class));

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessageWithMultipleAttachments());

            ArgumentCaptor<SendMailPostRequestBody> captor = ArgumentCaptor.forClass(SendMailPostRequestBody.class);
            verify(mockSendMailRequestBuilder).post(captor.capture());

            com.microsoft.graph.models.Message message = captor.getValue().getMessage();
            assertNotNull("Attachments should not be null", message.getAttachments());
            assertEquals("Should have two attachments", 2, message.getAttachments().size());
        }

        @Test
        public void testHtmlBodyContent() throws Exception
        {
            doNothing().when(mockSendMailRequestBuilder).post(any(SendMailPostRequestBody.class));

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessageWithHtmlBody());

            ArgumentCaptor<SendMailPostRequestBody> captor = ArgumentCaptor.forClass(SendMailPostRequestBody.class);
            verify(mockSendMailRequestBuilder).post(captor.capture());

            com.microsoft.graph.models.Message message = captor.getValue().getMessage();
            assertNotNull("Body should not be null", message.getBody());
            assertEquals(BodyType.Html, message.getBody().getContentType());
            assertTrue("Body should contain HTML", message.getBody().getContent().contains("<html>"));
        }

        /**
         * Creates a GraphTransportProvider with a mock GraphServiceClient.
         */
        private GraphTransportProvider createTestProvider()
        {
            GraphTransportProvider provider = new GraphTransportProvider()
            {
                @Override
                protected GraphServiceClient createGraphServiceClient()
                {
                    return mockGraphClient;
                }
            };

            Properties props = provider.getProperties();
            props.put("mail.graph.tenantId", TEST_TENANT_ID);
            props.put("mail.graph.clientId", TEST_CLIENT_ID);
            props.put("mail.graph.clientSecret", TEST_CLIENT_SECRET);
            props.put("mail.graph.fromAddress", TEST_FROM_ADDRESS);

            return provider;
        }

        private MimeMessage createTestMessage() throws Exception
        {
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(TEST_FROM_ADDRESS));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(TEST_TO_ADDRESS));
            message.setSubject("Test email from GraphTransportProviderTest");
            message.setText("This is a test email body.");
            return message;
        }

        private MimeMessage createTestMessageWithAttachment() throws Exception
        {
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(TEST_FROM_ADDRESS));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(TEST_TO_ADDRESS));
            message.setSubject("Test email with attachment");

            Multipart multipart = new MimeMultipart();

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("This is a test email with attachment.");
            multipart.addBodyPart(textPart);

            MimeBodyPart attachmentPart = new MimeBodyPart();
            byte[] attachmentContent = "This is the attachment content.".getBytes(StandardCharsets.UTF_8);
            DataSource dataSource = new ByteArrayDataSource(attachmentContent, "text/plain");
            attachmentPart.setDataHandler(new DataHandler(dataSource));
            attachmentPart.setFileName("test.txt");
            attachmentPart.setDisposition(MimeBodyPart.ATTACHMENT);
            multipart.addBodyPart(attachmentPart);

            message.setContent(multipart);
            return message;
        }

        private MimeMessage createTestMessageWithLargeAttachment() throws Exception
        {
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(TEST_FROM_ADDRESS));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(TEST_TO_ADDRESS));
            message.setSubject("Test email with large attachment");

            Multipart multipart = new MimeMultipart();

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("This is a test email with a large attachment.");
            multipart.addBodyPart(textPart);

            MimeBodyPart attachmentPart = new MimeBodyPart();
            byte[] largeContent = new byte[4 * 1024 * 1024]; // 4MB
            DataSource dataSource = new ByteArrayDataSource(largeContent, "application/octet-stream");
            attachmentPart.setDataHandler(new DataHandler(dataSource));
            attachmentPart.setFileName("large-file.bin");
            attachmentPart.setDisposition(MimeBodyPart.ATTACHMENT);
            multipart.addBodyPart(attachmentPart);

            message.setContent(multipart);
            return message;
        }

        private MimeMessage createTestMessageWithInlineAttachment() throws Exception
        {
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(TEST_FROM_ADDRESS));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(TEST_TO_ADDRESS));
            message.setSubject("Test email with inline attachment");

            Multipart multipart = new MimeMultipart("related");

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent("<html><body><p>See image:</p><img src=\"cid:image001\"/></body></html>", "text/html");
            multipart.addBodyPart(htmlPart);

            MimeBodyPart imagePart = new MimeBodyPart();
            byte[] pngData = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            DataSource dataSource = new ByteArrayDataSource(pngData, "image/png");
            imagePart.setDataHandler(new DataHandler(dataSource));
            imagePart.setFileName("image.png");
            imagePart.setDisposition(MimeBodyPart.INLINE);
            imagePart.setContentID("<image001>");
            multipart.addBodyPart(imagePart);

            message.setContent(multipart);
            return message;
        }

        private MimeMessage createTestMessageWithMultipleAttachments() throws Exception
        {
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(TEST_FROM_ADDRESS));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(TEST_TO_ADDRESS));
            message.setSubject("Test email with multiple attachments");

            Multipart multipart = new MimeMultipart();

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("This is a test email with multiple attachments.");
            multipart.addBodyPart(textPart);

            MimeBodyPart attachment1 = new MimeBodyPart();
            byte[] content1 = "First attachment content.".getBytes(StandardCharsets.UTF_8);
            DataSource dataSource1 = new ByteArrayDataSource(content1, "text/plain");
            attachment1.setDataHandler(new DataHandler(dataSource1));
            attachment1.setFileName("file1.txt");
            attachment1.setDisposition(MimeBodyPart.ATTACHMENT);
            multipart.addBodyPart(attachment1);

            MimeBodyPart attachment2 = new MimeBodyPart();
            byte[] content2 = "Second attachment content.".getBytes(StandardCharsets.UTF_8);
            DataSource dataSource2 = new ByteArrayDataSource(content2, "text/plain");
            attachment2.setDataHandler(new DataHandler(dataSource2));
            attachment2.setFileName("file2.txt");
            attachment2.setDisposition(MimeBodyPart.ATTACHMENT);
            multipart.addBodyPart(attachment2);

            message.setContent(multipart);
            return message;
        }

        private MimeMessage createTestMessageWithHtmlBody() throws Exception
        {
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(TEST_FROM_ADDRESS));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(TEST_TO_ADDRESS));
            message.setSubject("Test email with HTML body");
            message.setContent("<html><body><h1>Hello</h1><p>This is an HTML email.</p></body></html>", "text/html");
            return message;
        }
    }
}
