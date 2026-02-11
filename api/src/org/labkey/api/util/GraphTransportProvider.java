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
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.lib.legacy.ClassImposteriser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Microsoft Graph API email transport provider.
 * Sends email via Graph API using OAuth2 client credentials.
 * Uses the Microsoft Graph SDK and Azure Identity library for authentication and API calls.
 */
public class GraphTransportProvider implements EmailTransportProvider
{
    private static final org.apache.logging.log4j.Logger LOG = LogHelper.getLogger(GraphTransportProvider.class, "Microsoft Graph Transport Provider");

    // Graph API has a hard ~4MB limit on request body size. Unlike SMTP (which typically allows 10-25MB),
    // this means we must handle large content differently: attachments over 3MB require upload sessions,
    // and data URIs in HTML must be converted to CID attachments to avoid exceeding the limit.
    // See: https://learn.microsoft.com/en-us/graph/outlook-large-attachments

    // Size threshold for using upload sessions (3MB) - smaller attachments use inline base64 encoding
    private static final int LARGE_ATTACHMENT_THRESHOLD = 3 * 1024 * 1024;

    // Maximum body size limit (3.5MB) - leaves room for other message JSON content within Graph API's ~4MB limit
    private static final int MAX_BODY_SIZE = (int) (3.5 * 1024 * 1024);

    // Note: Retry logic for transient failures (429, 503, 504) is handled automatically by the
    // Graph SDK's built-in RetryHandler middleware. No custom retry logic needed here.

    private final Properties _properties = new Properties();

    // GraphServiceClient is the SDK's entry point that translates fluent method calls into HTTP
    // requests to Microsoft Graph API endpoints like /users/{id}/sendMail and /users/{id}/messages.
    // Lazily initialized - created on first use after configuration is loaded.
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
     * For messages with large attachments, use the draft and upload session approach.
     * For messages without attachments or with small attachments, use direct sendMail.
     */
    private void sendMessage(MimeMessage mm) throws IOException, MessagingException
    {
        // Step 1: Extract MIME attachments from the message structure
        List<AttachmentInfo> attachments = extractAttachments(mm);

        // Step 2: Build the Graph message. This also scans the HTML body for data URIs and converts
        // them to CID attachments, adding them to the attachment list.
        com.microsoft.graph.models.Message graphMessage = buildGraphMessage(mm, attachments);

        // Step 3: Check if any attachment (including converted data URIs) exceeds the threshold.
        // This check must happen AFTER buildGraphMessage because data URI conversion may add
        // large attachments that weren't in the original MIME structure.
        boolean hasLargeAttachments = attachments.stream()
                .anyMatch(a -> a.content().length > LARGE_ATTACHMENT_THRESHOLD);

        if (hasLargeAttachments)
        {
            // Has large attachments - create draft, upload via upload sessions, then send
            sendWithLargeAttachments(graphMessage, attachments);
        }
        else
        {
            // No attachments or small attachments - send directly via sendMail
            sendViaSdk(graphMessage, attachments);
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
        try (InputStream is = part.getInputStream())
        {
            return is.readAllBytes();
        }
    }

    /**
     * Send email using the Graph SDK with inline attachments (for messages without attachments
     * or with attachments smaller than 3MB).
     *
     * @param graphMessage the already-built Graph message (from buildGraphMessage)
     * @param attachments all attachments including any converted from data URIs
     */
    private void sendViaSdk(com.microsoft.graph.models.Message graphMessage, List<AttachmentInfo> attachments)
    {
        GraphServiceClient client = getGraphClient();
        String fromAddress = getFromAddress();

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
     *
     * @param graphMessage the already-built Graph message (from buildGraphMessage)
     * @param attachments all attachments including any converted from data URIs
     */
    private void sendWithLargeAttachments(com.microsoft.graph.models.Message graphMessage, List<AttachmentInfo> attachments)
            throws IOException
    {
        GraphServiceClient client = getGraphClient();
        String fromAddress = getFromAddress();

        // Step 1: Create draft message from the pre-built Graph message
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
     * <p>
     * If the HTML body contains data URIs (e.g., {@code <img src="data:image/png;base64,...">}),
     * they are converted to CID attachments following email industry best practices. The data URI
     * is decoded, added to the attachments list, and replaced with a cid: reference in the HTML.
     * <p>
     * Why we handle this ourselves: The Microsoft Graph SDK is a REST API wrapper that sends
     * whatever content you provide - it doesn't parse or transform HTML bodies. Data URIs embedded
     * in HTML can cause issues: (1) they bloat the message body and may exceed Graph API's ~4MB
     * request limit, (2) some email clients (e.g., Gmail) block base64 data URIs for security
     * reasons. The proper email standard is to use CID (Content-ID) attachments with
     * {@code <img src="cid:...">} references, which this method implements.
     *
     * @param mm the MimeMessage to convert
     * @param attachments list of attachments; any data URIs converted from the HTML body are added here
     */
    private com.microsoft.graph.models.Message buildGraphMessage(MimeMessage mm, List<AttachmentInfo> attachments)
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
            // HTML content - scan for data URIs and convert them to CID attachments.
            // Data URIs like "data:image/png;base64,..." are decoded and added to attachments list,
            // then replaced with "cid:<generated-id>" references in the HTML.
            String html = convertDataUrisToCidAttachments(bodyContent[0], attachments);
            validateBodySize(html);
            body.setContentType(BodyType.Html);
            body.setContent(html);
        }
        else if (bodyContent[1] != null)
        {
            // Plain text content - no data URI conversion needed
            validateBodySize(bodyContent[1]);
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
        message.setToRecipients(convertRecipients(mm.getRecipients(Message.RecipientType.TO)));
        message.setCcRecipients(convertRecipients(mm.getRecipients(Message.RecipientType.CC)));
        message.setBccRecipients(convertRecipients(mm.getRecipients(Message.RecipientType.BCC)));

        // Reply-To (set by EmailTemplate via setHeader("Reply-To", ...))
        Address[] replyTo = mm.getReplyTo();
        Address[] from = mm.getFrom();
        // MimeMessage.getReplyTo() falls back to getFrom() when no Reply-To header is set,
        // so only map it when it differs from From (i.e., an explicit Reply-To was set).
        if (replyTo != null && replyTo.length > 0 && !java.util.Arrays.equals(replyTo, from))
        {
            message.setReplyTo(convertRecipients(replyTo));
        }

        return message;
    }

    /**
     * Convert an array of JavaMail addresses to a list of Graph Recipient objects.
     */
    private List<Recipient> convertRecipients(Address[] addresses)
    {
        if (addresses == null || addresses.length == 0)
        {
            return List.of();
        }

        List<Recipient> recipients = new ArrayList<>();
        for (Address address : addresses)
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
        return recipients;
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
            String contentType = getEffectiveContentType(mm);
            boolean isHtml = contentType != null && contentType.toLowerCase().contains("text/html");
            if (isHtml)
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
            String contentType = getEffectiveContentType(part);

            LOG.debug("Part {}: disposition={}, contentType={}", i, disposition, contentType);

            // Skip attachments - this method only extracts body content (text/html or text/plain).
            // Attachments are handled separately by extractAttachments() in sendMessage().
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
                boolean isHtml = contentType != null && contentType.toLowerCase().contains("text/html");

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

    /**
     * Get the effective content type of Part. {@code MimeBodyPart.getContentType()} reads from the
     * Content-Type header, which may not be set after {@code setContent(obj, type)} — that method stores
     * the type on the DataHandler but clears the header, causing {@code getContentType()} to default to
     * "text/plain". The DataHandler always preserves the actual content type.
     */
    private String getEffectiveContentType(Part part) throws MessagingException
    {
        try
        {
            jakarta.activation.DataHandler dh = part.getDataHandler();
            if (dh != null)
            {
                return dh.getContentType();
            }
        }
        catch (MessagingException ignored) {}
        return part.getContentType();
    }

    // Pattern to match data URIs in HTML (e.g., src="data:image/png;base64,...")
    // Captures: group 1 = quote char, group 2 = MIME type, group 3 = base64 data
    private static final java.util.regex.Pattern DATA_URI_PATTERN =
            java.util.regex.Pattern.compile(
                    "([\"'])data:([^;]+);base64,([^\"']+)\\1",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    // Counter for generating unique Content-IDs for converted data URIs.
    // Uses AtomicInteger for thread safety since multiple threads may send emails concurrently.
    private final AtomicInteger _dataUriCounter = new AtomicInteger(0);

    /**
     * Scan HTML content for base64 data URIs and convert them to CID attachments.
     * This follows email industry best practices - embedded images should be sent as
     * MIME attachments with Content-ID references rather than inline data URIs.
     *
     * @param html the HTML content to scan
     * @param attachments list to add converted attachments to
     * @return updated HTML with data URIs replaced by cid: references
     */
    private String convertDataUrisToCidAttachments(String html, List<AttachmentInfo> attachments)
    {
        if (html == null || !html.contains("data:"))
        {
            return html;
        }

        java.util.regex.Matcher matcher = DATA_URI_PATTERN.matcher(html);
        StringBuilder result = new StringBuilder();

        while (matcher.find())
        {
            String quote = matcher.group(1);
            String mimeType = matcher.group(2);
            String base64Data = matcher.group(3);

            try
            {
                // Decode the base64 content. Use MIME decoder which is lenient about
                // padding and whitespace, as real-world data URIs may not be strictly formatted.
                byte[] content = java.util.Base64.getMimeDecoder().decode(base64Data);

                // Generate a unique Content-ID
                String contentId = "datauri-" + System.currentTimeMillis() + "-" + _dataUriCounter.incrementAndGet();

                // Determine file extension from MIME type
                String extension = getExtensionForMimeType(mimeType);
                String fileName = contentId + extension;

                // Create attachment info and add to list
                attachments.add(new AttachmentInfo(fileName, mimeType, content, contentId));

                // Replace data URI with cid: reference
                matcher.appendReplacement(result, quote + "cid:" + contentId + quote);

                LOG.debug("Converted data URI to CID attachment: {} ({} bytes, type: {})",
                        contentId, content.length, mimeType);
            }
            catch (IllegalArgumentException e)
            {
                // Invalid base64 - leave the data URI as-is
                LOG.warn("Failed to decode base64 data URI, leaving as-is: {}", e.getMessage());
                matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Get file extension for common MIME types.
     */
    private String getExtensionForMimeType(String mimeType)
    {
        if (mimeType == null) return "";
        return switch (mimeType.toLowerCase())
        {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            case "image/bmp" -> ".bmp";
            case "image/tiff" -> ".tiff";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    /**
     * Validate that body content doesn't exceed the Graph API size limit.
     *
     * @param bodyContent the body content (HTML or text)
     * @throws MessagingException if body exceeds MAX_BODY_SIZE
     */
    private void validateBodySize(String bodyContent) throws MessagingException
    {
        if (bodyContent != null)
        {
            int bodySize = bodyContent.getBytes(StandardCharsets.UTF_8).length;
            if (bodySize > MAX_BODY_SIZE)
            {
                throw new MessagingException(String.format(
                        "Email body size (%d bytes) exceeds maximum allowed size (%d bytes). " +
                        "Consider moving large embedded content to attachments.",
                        bodySize, MAX_BODY_SIZE));
            }
        }
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

        // Perform the chunked upload
        performLargeFileUpload(client, uploadSession, attachment);
    }

    /**
     * Perform the actual chunked upload using LargeFileUploadTask.
     * Extracted to a protected method to allow tests to override and skip the actual HTTP calls.
     */
    protected void performLargeFileUpload(GraphServiceClient client, UploadSession uploadSession,
                                          AttachmentInfo attachment) throws IOException
    {
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
     * Unit tests for GraphTransportProvider using JMock to mock the Microsoft Graph SDK.
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

        private Mockery mockery;
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

        // Captured value for verification (JMock alternative to ArgumentCaptor)
        private SendMailPostRequestBody capturedSendMailRequest;

        /**
         * Custom JMock Action to capture SendMailPostRequestBody for later verification.
         */
        private class CaptureSendMailAction implements Action
        {
            @Override
            public Object invoke(Invocation invocation)
            {
                capturedSendMailRequest = (SendMailPostRequestBody) invocation.getParameter(0);
                return null;
            }

            @Override
            public void describeTo(Description description)
            {
                description.appendText("captures SendMailPostRequestBody argument");
            }
        }

        @Before
        public void setUp()
        {
            // Create mockery with ClassImposteriser to mock concrete classes (not just interfaces)
            mockery = new Mockery();
            mockery.setImposteriser(ClassImposteriser.INSTANCE);
            mockery.setThreadingPolicy(new org.jmock.lib.concurrent.Synchroniser());

            // Create mocks
            mockGraphClient = mockery.mock(GraphServiceClient.class);
            mockRequestAdapter = mockery.mock(RequestAdapter.class);
            mockUsersRequestBuilder = mockery.mock(UsersRequestBuilder.class);
            mockUserItemRequestBuilder = mockery.mock(UserItemRequestBuilder.class);
            mockSendMailRequestBuilder = mockery.mock(SendMailRequestBuilder.class);
            mockMessagesRequestBuilder = mockery.mock(MessagesRequestBuilder.class);
            mockMessageItemRequestBuilder = mockery.mock(MessageItemRequestBuilder.class);
            mockAttachmentsRequestBuilder = mockery.mock(AttachmentsRequestBuilder.class);
            mockCreateUploadSessionRequestBuilder = mockery.mock(CreateUploadSessionRequestBuilder.class);
            mockSendRequestBuilder = mockery.mock(SendRequestBuilder.class);

            // Reset captured values
            capturedSendMailRequest = null;
        }

        private void setUpBasicExpectations()
        {
            mockery.checking(new Expectations() {{
                // Wire up the mock chain
                allowing(mockGraphClient).getRequestAdapter();
                will(returnValue(mockRequestAdapter));

                allowing(mockGraphClient).users();
                will(returnValue(mockUsersRequestBuilder));

                allowing(mockUsersRequestBuilder).byUserId(with(any(String.class)));
                will(returnValue(mockUserItemRequestBuilder));

                allowing(mockUserItemRequestBuilder).sendMail();
                will(returnValue(mockSendMailRequestBuilder));

                allowing(mockUserItemRequestBuilder).messages();
                will(returnValue(mockMessagesRequestBuilder));

                allowing(mockMessagesRequestBuilder).byMessageId(with(any(String.class)));
                will(returnValue(mockMessageItemRequestBuilder));

                allowing(mockMessageItemRequestBuilder).attachments();
                will(returnValue(mockAttachmentsRequestBuilder));

                allowing(mockMessageItemRequestBuilder).send();
                will(returnValue(mockSendRequestBuilder));

                allowing(mockAttachmentsRequestBuilder).createUploadSession();
                will(returnValue(mockCreateUploadSessionRequestBuilder));
            }});
        }

        @Test
        public void testSuccessfulEmailSend() throws Exception
        {
            setUpBasicExpectations();
            mockery.checking(new Expectations() {{
                oneOf(mockSendMailRequestBuilder).post(with(any(SendMailPostRequestBody.class)));
                will(new CaptureSendMailAction());
            }});

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessage());

            mockery.assertIsSatisfied();
            assertNotNull("Request body should not be null", capturedSendMailRequest);
            assertNotNull("Message should not be null", capturedSendMailRequest.getMessage());
            assertEquals("Test email from GraphTransportProviderTest", capturedSendMailRequest.getMessage().getSubject());
            assertNotEquals("Should not save to sent items", Boolean.TRUE, capturedSendMailRequest.getSaveToSentItems());
        }

        @Test
        public void testEmailWithRecipients() throws Exception
        {
            setUpBasicExpectations();
            mockery.checking(new Expectations() {{
                oneOf(mockSendMailRequestBuilder).post(with(any(SendMailPostRequestBody.class)));
                will(new CaptureSendMailAction());
            }});

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessage());

            mockery.assertIsSatisfied();
            com.microsoft.graph.models.Message message = capturedSendMailRequest.getMessage();
            assertNotNull("To recipients should not be null", message.getToRecipients());
            assertEquals("Should have one recipient", 1, message.getToRecipients().size());
            assertEquals(TEST_TO_ADDRESS, message.getToRecipients().get(0).getEmailAddress().getAddress());
        }

        @Test
        public void testEmailWithCcAndBccRecipients() throws Exception
        {
            setUpBasicExpectations();
            mockery.checking(new Expectations() {{
                oneOf(mockSendMailRequestBuilder).post(with(any(SendMailPostRequestBody.class)));
                will(new CaptureSendMailAction());
            }});

            GraphTransportProvider provider = createTestProvider();

            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(TEST_FROM_ADDRESS));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(TEST_TO_ADDRESS));
            message.addRecipient(Message.RecipientType.CC, new InternetAddress("cc@example.com"));
            message.addRecipient(Message.RecipientType.BCC, new InternetAddress("bcc@example.com"));
            message.setSubject("Test email with CC and BCC");
            message.setText("Body text.");

            provider.send(message);

            mockery.assertIsSatisfied();
            com.microsoft.graph.models.Message graphMessage = capturedSendMailRequest.getMessage();

            assertNotNull("To recipients should not be null", graphMessage.getToRecipients());
            assertEquals(1, graphMessage.getToRecipients().size());
            assertEquals(TEST_TO_ADDRESS, graphMessage.getToRecipients().get(0).getEmailAddress().getAddress());

            assertNotNull("CC recipients should not be null", graphMessage.getCcRecipients());
            assertEquals(1, graphMessage.getCcRecipients().size());
            assertEquals("cc@example.com", graphMessage.getCcRecipients().get(0).getEmailAddress().getAddress());

            assertNotNull("BCC recipients should not be null", graphMessage.getBccRecipients());
            assertEquals(1, graphMessage.getBccRecipients().size());
            assertEquals("bcc@example.com", graphMessage.getBccRecipients().get(0).getEmailAddress().getAddress());
        }

        @Test
        public void testEmailWithReplyTo() throws Exception
        {
            setUpBasicExpectations();
            mockery.checking(new Expectations() {{
                oneOf(mockSendMailRequestBuilder).post(with(any(SendMailPostRequestBody.class)));
                will(new CaptureSendMailAction());
            }});

            GraphTransportProvider provider = createTestProvider();

            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(TEST_FROM_ADDRESS));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(TEST_TO_ADDRESS));
            message.setSubject("Test email with Reply-To");
            // Set Reply-To the same way EmailTemplate does
            message.setHeader("Reply-To", "replyto@example.com");
            message.setText("Body text.");

            provider.send(message);

            mockery.assertIsSatisfied();
            com.microsoft.graph.models.Message graphMessage = capturedSendMailRequest.getMessage();

            assertNotNull("ReplyTo should not be null", graphMessage.getReplyTo());
            assertEquals(1, graphMessage.getReplyTo().size());
            assertEquals("replyto@example.com", graphMessage.getReplyTo().get(0).getEmailAddress().getAddress());
        }

        @Test
        public void testEmailWithoutExplicitReplyToOmitsIt() throws Exception
        {
            setUpBasicExpectations();
            mockery.checking(new Expectations() {{
                oneOf(mockSendMailRequestBuilder).post(with(any(SendMailPostRequestBody.class)));
                will(new CaptureSendMailAction());
            }});

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessage());

            mockery.assertIsSatisfied();
            com.microsoft.graph.models.Message graphMessage = capturedSendMailRequest.getMessage();

            // When no explicit Reply-To is set, it should not be mapped (Graph defaults to From)
            assertNull("ReplyTo should be null when not explicitly set", graphMessage.getReplyTo());
        }

        @Test
        public void testSuccessfulEmailWithSmallAttachment() throws Exception
        {
            setUpBasicExpectations();
            mockery.checking(new Expectations() {{
                oneOf(mockSendMailRequestBuilder).post(with(any(SendMailPostRequestBody.class)));
                will(new CaptureSendMailAction());
            }});

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessageWithAttachment());

            mockery.assertIsSatisfied();
            com.microsoft.graph.models.Message message = capturedSendMailRequest.getMessage();
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
            // For large attachments (>3MB), creates draft, uploads via upload session, then sends
            com.microsoft.graph.models.Message draftMessage = new com.microsoft.graph.models.Message();
            draftMessage.setId(TEST_MESSAGE_ID);

            UploadSession uploadSession = new UploadSession();
            uploadSession.setUploadUrl(TEST_UPLOAD_URL);

            setUpBasicExpectations();

            mockery.checking(new Expectations() {{
                // Mock draft creation
                oneOf(mockMessagesRequestBuilder).post(with(any(com.microsoft.graph.models.Message.class)));
                will(returnValue(draftMessage));

                // Mock upload session creation
                oneOf(mockCreateUploadSessionRequestBuilder).post(with(any(CreateUploadSessionPostRequestBody.class)));
                will(returnValue(uploadSession));

                // Mock send (after upload completes)
                oneOf(mockSendRequestBuilder).post();

                // Mock draft deletion (for cleanup on failure) - may or may not be called
                allowing(mockMessageItemRequestBuilder).delete();

                // sendMail should NOT be called (large attachments use draft+upload+send flow)
                never(mockSendMailRequestBuilder).post(with(any(SendMailPostRequestBody.class)));
            }});

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessageWithLargeAttachment());

            mockery.assertIsSatisfied();
        }

        @Test
        public void testLargeAttachmentUploadFailureDeletesDraft() throws Exception
        {
            com.microsoft.graph.models.Message draftMessage = new com.microsoft.graph.models.Message();
            draftMessage.setId(TEST_MESSAGE_ID);

            setUpBasicExpectations();
            mockery.checking(new Expectations() {{
                // Mock draft creation
                oneOf(mockMessagesRequestBuilder).post(with(any(com.microsoft.graph.models.Message.class)));
                will(returnValue(draftMessage));

                // Mock upload session creation to return null (simulating failure)
                oneOf(mockCreateUploadSessionRequestBuilder).post(with(any(CreateUploadSessionPostRequestBody.class)));
                will(returnValue(null));

                // Mock draft deletion - should be called on failure
                oneOf(mockMessageItemRequestBuilder).delete();
            }});

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

            mockery.assertIsSatisfied();
        }

        @Test
        public void testEmailWithInlineAttachment() throws Exception
        {
            setUpBasicExpectations();
            mockery.checking(new Expectations() {{
                oneOf(mockSendMailRequestBuilder).post(with(any(SendMailPostRequestBody.class)));
                will(new CaptureSendMailAction());
            }});

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessageWithInlineAttachment());

            mockery.assertIsSatisfied();
            com.microsoft.graph.models.Message message = capturedSendMailRequest.getMessage();
            assertNotNull("Attachments should not be null", message.getAttachments());
            assertEquals("Should have one attachment", 1, message.getAttachments().size());

            FileAttachment attachment = (FileAttachment) message.getAttachments().get(0);
            assertTrue("Attachment should be marked as inline", attachment.getIsInline());
            assertEquals("image001", attachment.getContentId());
        }

        @Test
        public void testEmailWithMultipleAttachments() throws Exception
        {
            setUpBasicExpectations();
            mockery.checking(new Expectations() {{
                oneOf(mockSendMailRequestBuilder).post(with(any(SendMailPostRequestBody.class)));
                will(new CaptureSendMailAction());
            }});

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessageWithMultipleAttachments());

            mockery.assertIsSatisfied();
            com.microsoft.graph.models.Message message = capturedSendMailRequest.getMessage();
            assertNotNull("Attachments should not be null", message.getAttachments());
            assertEquals("Should have two attachments", 2, message.getAttachments().size());
        }

        @Test
        public void testHtmlBodyContent() throws Exception
        {
            setUpBasicExpectations();
            mockery.checking(new Expectations() {{
                oneOf(mockSendMailRequestBuilder).post(with(any(SendMailPostRequestBody.class)));
                will(new CaptureSendMailAction());
            }});

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessageWithHtmlBody());

            mockery.assertIsSatisfied();
            com.microsoft.graph.models.Message message = capturedSendMailRequest.getMessage();
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

                @Override
                protected void performLargeFileUpload(GraphServiceClient client, UploadSession uploadSession,
                                                      AttachmentInfo attachment)
                {
                    // Skip actual chunked upload in tests - LargeFileUploadTask requires
                    // real Kiota serialization infrastructure that can't be easily mocked
                    LOG.debug("Test mode: skipping chunked upload for '{}'", attachment.name());
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

        private MimeMessage createTestMessageWithDataUri() throws Exception
        {
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(TEST_FROM_ADDRESS));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(TEST_TO_ADDRESS));
            message.setSubject("Test email with data URI");
            // Small PNG: 1x1 red pixel
            String base64Png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
            String html = "<html><body><p>Image:</p><img src=\"data:image/png;base64," + base64Png + "\"/></body></html>";
            message.setContent(html, "text/html");
            return message;
        }

        @Test
        public void testDataUriConvertedToCidAttachment() throws Exception
        {
            setUpBasicExpectations();
            mockery.checking(new Expectations() {{
                oneOf(mockSendMailRequestBuilder).post(with(any(SendMailPostRequestBody.class)));
                will(new CaptureSendMailAction());
            }});

            GraphTransportProvider provider = createTestProvider();
            provider.send(createTestMessageWithDataUri());

            mockery.assertIsSatisfied();
            com.microsoft.graph.models.Message message = capturedSendMailRequest.getMessage();

            // Verify HTML body now has cid: reference instead of data URI
            assertNotNull("Body should not be null", message.getBody());
            assertEquals(BodyType.Html, message.getBody().getContentType());
            String bodyContent = message.getBody().getContent();
            assertTrue("Body should contain cid: reference", bodyContent.contains("cid:"));
            assertFalse("Body should not contain data URI", bodyContent.contains("data:image/png;base64"));

            // Verify attachment was created from the data URI
            assertNotNull("Attachments should not be null", message.getAttachments());
            assertEquals("Should have one attachment (converted from data URI)", 1, message.getAttachments().size());

            FileAttachment attachment = (FileAttachment) message.getAttachments().get(0);
            assertTrue("Attachment should be inline", attachment.getIsInline());
            assertNotNull("Attachment should have content ID", attachment.getContentId());
            assertEquals("image/png", attachment.getContentType());
        }
    }
}
