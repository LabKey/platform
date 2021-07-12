/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.util.emailTemplate;

import com.google.common.collect.Streams;
import org.apache.commons.collections4.ListUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.MissingFormatArgumentException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Admin-customizable template for sending automated emails from the server. Subclasses are used for each specific type
 * of email to send. A simple substitution syntax allows for replacing sections with dynamic content.
 *
 * User: Karl Lum
 * Date: Jan 15, 2007
 */
public abstract class EmailTemplate
{
    private static final Logger LOG = LogManager.getLogger(EmailTemplate.class);
    /** Pattern for recognizing substitution syntax, which is of the form ^TOKEN^ */
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("\\^(.*?)\\^");
    private static final List<ReplacementParam<?>> STANDARD_REPLACEMENTS = new ArrayList<>();
    /** Separates the token name from how it should be formatted (for date and numeric values)  */
    private static final String FORMAT_DELIMITER = "|";

    protected static final String DEFAULT_SENDER = "^siteShortName^";
    protected static final String DEFAULT_REPLY_TO = "^siteEmailAddress^";

    /**
     * Distinguishes between the types of email that might be sent. Additionally, used to ensure correct encoding
     * of substitutions when generating the full email.
     */
    public enum ContentType
    {
        Plain
        {
            @Override
            public String format(String sourceValue, ContentType sourceType)
            {
                if (sourceType != Plain)
                {
                    // We don't support converting from HTML to plain
                    throw new IllegalArgumentException("Unable to convert from " + sourceType + " to Plain");
                }
                return sourceValue;
            }
        },
        HTML
        {
            @Override
            public String format(String sourceValue, ContentType sourceType)
            {
                if (sourceType == HTML)
                {
                    return sourceValue;
                }
                return PageFlowUtil.filter(sourceValue, true, false);
            }
        };

        /** Render the given sourceValue into the target (this) type, based on the sourceType */
        public abstract String format(String sourceValue, ContentType sourceType);
    }

    /** Defines whether a template can be customized on just the site-level, or also on a per-folder basis */
    public enum Scope
    {
        Site
        {
            @Override
            public boolean isEditableIn(Container c)
            {
                return c.isRoot();
            }
        },
        SiteOrFolder
        {
            @Override
            public boolean isEditableIn(Container c)
            {
                return true;
            }};

        public abstract boolean isEditableIn(Container c);
    }

    // These members are final and immutable
    @NotNull private final String _name;
    private final String _description;
    /** The format of the email to be generated */
    @NotNull private final ContentType _contentType;
    /** Scope is the locations in which the user should be able to edit this template. It should always be the same
     * for a given subclass, regardless of the instances */
    private final Scope _scope;

    // These members are mutable since they can be overridden by customizing the email template
    private String _body;
    private String _subject;
    @Nullable private String _senderName = DEFAULT_SENDER;
    @Nullable private String _replyToEmail = DEFAULT_REPLY_TO;
    /**
     * Container in which this template is stored. Null for the default templates defined in code, the root
     * container for site level templates, or a specific folder.
     */
    private Container _container = null;

    private List<ReplacementParam<?>> _customReplacements = null;

    static
    {
        Replacements replacements = new Replacements(STANDARD_REPLACEMENTS);
        replacements.add("organizationName", String.class, "Organization name (look and feel settings)", ContentType.Plain, c -> LookAndFeelProperties.getInstance(c).getCompanyName());
        replacements.add("siteShortName", String.class, "Header short name", ContentType.Plain, c -> LookAndFeelProperties.getInstance(c).getShortName());
        replacements.add("siteEmailAddress", String.class, "System email address", ContentType.Plain, c -> LookAndFeelProperties.getInstance(c).getSystemEmailAddress());
        replacements.add("contextPath", String.class, "Web application context path", ContentType.Plain, c -> AppProps.getInstance().getContextPath());
        replacements.add("supportLink", String.class, "Page where users can request support", ContentType.Plain, c -> LookAndFeelProperties.getInstance(c).getReportAProblemPath());
        replacements.add("systemDescription", String.class, "Header description", ContentType.Plain, c -> LookAndFeelProperties.getInstance(c).getDescription());
        replacements.add("systemEmail", String.class, "From address for system notification emails", ContentType.Plain, c -> LookAndFeelProperties.getInstance(c).getSystemEmailAddress());
        replacements.add("currentDateTime", Date.class, "Current date and time of the server", ContentType.Plain, c -> new Date());
        replacements.add("folderName", String.class, "Name of the folder that generated the email, if it is scoped to a folder", ContentType.Plain, c -> c.isRoot() ? null : c.getName());
        replacements.add("folderPath", String.class, "Full path of the folder that generated the email, if it is scoped to a folder", ContentType.Plain, c -> c.isRoot() ? null : c.getPath());
        replacements.add("folderURL", String.class, "URL to the folder that generated the email, if it is scoped to a folder", ContentType.Plain, c -> c.isRoot() ? null : PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c).getURIString());
        replacements.add("homePageURL", String.class, "The home page of this installation", ContentType.Plain, c -> ActionURL.getBaseServerURL()); // TODO: Use AppProps.getHomePageUrl() instead?
    }

    public EmailTemplate(@NotNull String name, String description, String subject, String body, @NotNull ContentType contentType, Scope scope)
    {
        _name = name;
        _description = description;
        _subject = subject;
        _body = body;
        _contentType = contentType;
        _scope = scope;
    }

    // Getters for immutable members
    @NotNull public String getName(){return _name;}
    public String getDescription(){return _description;}
    @NotNull public ContentType getContentType()
    {
        return _contentType;
    }
    public Scope getEditableScope(){return _scope;}

    // Getters/setters for mutable members
    public String getSubject(){return _subject;}
    public void setSubject(String subject){_subject = subject;}
    public String getBody(){return _body;}
    public void setBody(String body){_body = body;}
    public Container getContainer(){return _container;}
    /* package */ void setContainer(Container c){_container = c;}
    @Nullable public String getSenderName(){return _senderName;}
    public void setSenderName(@Nullable String senderName){_senderName = senderName;}
    @Nullable public String getReplyToEmail(){return _replyToEmail;}
    public void setReplyToEmail(@Nullable String senderEmail){_replyToEmail = senderEmail;}

    /**
     * Templates that declare themselves to have an HTML content type can also include a plain-text alternative
     * by using the boundary separator. Everything before the boundary is considered part of the HTML, and everything
     * after is part of the text.
     */
    public static String BODY_PART_BOUNDARY = "--text/html--boundary--";

    public HtmlString getHtmlBody()
    {
        if (getContentType() != ContentType.HTML)
        {
            throw new IllegalStateException("Cannot get the HTML for a template with content type " + getContentType());
        }

        return HtmlString.unsafe(_body.split(BODY_PART_BOUNDARY)[0]);
    }

    public String getTextBody()
    {
        if (getContentType() == ContentType.Plain)
        {
            return getBody();
        }

        String[] bodyParts = _body.split(BODY_PART_BOUNDARY);
        return bodyParts.length > 1 ? bodyParts[1] : null;
    }

    /** return true if this template has the capability of rendering both HTML and plain text variants in the same email */
    public boolean hasMultipleContentTypes()
    {
        return getContentType() == ContentType.HTML && _body.contains(BODY_PART_BOUNDARY);
    }

    abstract protected void addCustomReplacements(Replacements replacements);

    public boolean isValid(String[] error)
    {
        try
        {
            _validate(_subject);
            _validate(_body);
            return true;
        }
        catch (Exception e)
        {
            if (error != null && error.length >= 1)
                error[0] = e.getMessage();
            return false;
        }
    }

    protected boolean _validate(String text)
    {
        if (text != null)
        {
            Matcher m = SCRIPT_PATTERN.matcher(text);
            while (m.find())
            {
                String value = m.group(1);
                if (!isValidReplacement(value))
                    throw new IllegalArgumentException("Invalid template, the replacement parameter: " + value + " is unknown.");
            }
        }
        return true;
    }

    protected boolean isValidReplacement(String paramNameAndFormat)
    {
        String paramName = getParameterName(paramNameAndFormat);
        for (ReplacementParam<?> param : getAllReplacements())
        {
            if (param.getName().equalsIgnoreCase(paramName))
                return true;
        }
        return false;
    }

    private String getParameterName(String paramNameAndFormat)
    {
        int i = paramNameAndFormat.indexOf(FORMAT_DELIMITER);
        if (i != -1)
        {
            return paramNameAndFormat.substring(0, i);
        }
        return paramNameAndFormat;
    }

    private String getTemplateFormat(String paramNameAndFormat)
    {
        int i = paramNameAndFormat.indexOf(FORMAT_DELIMITER);
        if (i != -1)
        {
            return paramNameAndFormat.substring(i + 1);
        }
        return null;
    }

    public String getReplacement(Container c, String paramNameAndFormat, ContentType contentType)
    {
        String paramName = getParameterName(paramNameAndFormat);
        for (ReplacementParam<?> param : getAllReplacements())
        {
            if (param.getName().equalsIgnoreCase(paramName))
            {
                return param.getFormattedValue(c, getTemplateFormat(paramNameAndFormat), contentType);
            }
        }
        return null;
    }

    /** Sets the sender (with reply-to if needed), subject, and body of the email */
    public void renderAllToMessage(MailHelper.MultipartMessage message, Container c) throws MessagingException, UnsupportedEncodingException
    {
        String textBody = renderTextBody(c);
        if (_contentType.equals(ContentType.Plain))
        {
            if (textBody == null)
                textBody = renderBody(c);
            message.setTextContent(textBody);
        }
        else
        {
            if (hasMultipleContentTypes())
            {
                // HTML-formatted messages can also include a plain-text variant
                if (textBody != null)
                    message.setTextContent(textBody);
            }
            message.setEncodedHtmlContent(renderHtmlBody(c).toString());
        }
        message.setSubject(renderSubject(c));
        renderSenderToMessage(message, c);
    }

    /** Sets the sender (with reply-to if needed) of the email */
    public void renderSenderToMessage(MailHelper.MultipartMessage message, Container c) throws UnsupportedEncodingException, MessagingException
    {
        message.setFrom(renderFrom(c, LookAndFeelProperties.getInstance(c).getSystemEmailAddress()));
        String replyTo = renderReplyTo(c);
        if (replyTo != null)
        {
            message.setHeader("Reply-To", replyTo);
        }
    }

    public String renderReplyTo(Container c)
    {
        return render(c, getReplyToEmail());
    }

    public String renderSubject(Container c)
    {
        return render(c, getSubject(), ContentType.Plain);
    }

    public InternetAddress renderFrom(Container c, String senderEmail) throws UnsupportedEncodingException
    {
        // use system or folder default if senderEmail is missing
        if (senderEmail == null)
            senderEmail = LookAndFeelProperties.getInstance(c).getSystemEmailAddress();
        String senderDisplayName = render(c, getSenderName());
        return new InternetAddress(senderEmail, senderDisplayName);
    }

    public String renderSenderName(Container c)
    {
        return render(c, getSenderName());
    }

    public String renderBody(Container c)
    {
        return render(c, getBody());
    }

    public HtmlString renderHtmlBody(Container c)
    {
        String s = render(c, getHtmlBody().toString());
        return HtmlString.unsafe(s);
    }

    @Nullable
    public String renderTextBody(Container c)
    {
        return render(c, getTextBody(), ContentType.Plain);
    }

    protected String render(Container c, String text)
    {
        return render(c, text, _contentType);
    }

    protected String render(Container c, String text, ContentType contentType)
    {
        if (text == null)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Matcher m = SCRIPT_PATTERN.matcher(text);
        int start;
        int end = 0;
        while (m.find())
        {
            start = m.start();
            String value = m.group(1);
            sb.append(text, end, start);
            sb.append(getReplacement(c, value, contentType));
            end = m.end();
        }
        sb.append(text.substring(end));
        return sb.toString();
    }

    public List<ReplacementParam<?>> getStandardReplacements()
    {
        return STANDARD_REPLACEMENTS;
    }

    public static class Replacements
    {
        private final List<ReplacementParam<?>> _params;

        public Replacements(List<ReplacementParam<?>> params)
        {
            _params = params;
        }

        public void add(ReplacementParam<?> replacementParam)
        {
            assert !replacementExists(replacementParam) : "Replacement \"" + replacementParam.getName() + "\" already exists!";
            _params.add(replacementParam);
        }

        private boolean replacementExists(ReplacementParam<?> newParam)
        {
            return Streams.concat(STANDARD_REPLACEMENTS.stream(), _params.stream())
                .anyMatch(r -> r.getName().equalsIgnoreCase(newParam.getName()));
        }

        public <Type> void add(@NotNull String name, Class<Type> valueType, String description, ContentType contentType, Function<Container, Type> valueGetter)
        {
            add(new ReplacementParam<>(name, valueType, description, contentType)
            {
                @Override
                public Type getValue(Container c)
                {
                    return valueGetter.apply(c);
                }
            });
        }
    }

    public List<ReplacementParam<?>> getCustomReplacements()
    {
        if (null == _customReplacements)
        {
            _customReplacements = new ArrayList<>();
            addCustomReplacements(new Replacements(_customReplacements));
        }

        return _customReplacements;
    }

    public List<ReplacementParam<?>> getAllReplacements()
    {
        return ListUtils.union(getStandardReplacements(), getCustomReplacements());
    }

    @Deprecated // Old method should not be called or overridden! Leave this for a while to make sure...
    final public List<ReplacementParam<?>> getValidReplacements()
    {
        throw new IllegalStateException();
    }

    public static abstract class ReplacementParam<Type> implements Comparable<ReplacementParam<Type>>
    {
        @NotNull
        private final String _name;
        private final Class<Type> _valueType;
        private final String _description;
        private final ContentType _contentType;

        public ReplacementParam(@NotNull String name, Class<Type> valueType, String description, ContentType contentType)
        {
            _name = name;
            _valueType = valueType;
            _description = description;
            _contentType = contentType;
        }

        @NotNull public String getName(){return _name;}
        public String getDescription(){return _description;}
        public abstract Type getValue(Container c);

        public String getFormattedValue(Container c, @Nullable String templateFormat, ContentType emailFormat)
        {
            String formattedValue;
            Object value = getValue(c);
            if (value == null || "".equals(value))
            {
                formattedValue = "";
            }
            else
            {
                if (value instanceof String)
                {
                    // This may not be quite right, but seems OK given all of our current parameters
                    // We format just the value itself, not any surrounding text, but we can only safely do
                    // this for String values. That is the overwhelming majority of values. Other formatted
                    // values (e.g., dates) are encoded properly below.
                    value = emailFormat.format((String) value, getContentType());
                }

                if (templateFormat != null)
                {
                    try
                    {
                        // Don't encode formattedValue; template might include HTML (e.g., specimen request notification)
                        Formatter formatter = new Formatter();
                        formatter.format(templateFormat, value);
                        formattedValue = formatter.toString();
                    }
                    catch (MissingFormatArgumentException e)
                    {
                        LOG.warn("Unable to format value '" + value + "' using format string '" + templateFormat + "' in email template '" + getName() + "'");
                        formattedValue = value.toString();
                    }
                }
                else if (value instanceof Date)
                {
                    // Always encode formatted dates if we're rendering to HTML, #30986. Hard-code sourceType to Plain
                    // since we're transforming the value.
                    formattedValue = emailFormat.format(DateUtil.formatDateTime(c, (Date)value), ContentType.Plain);
                }
                else
                {
                    formattedValue = value.toString();
                }
            }
            return formattedValue;
        }

        public Class<Type> getValueType()
        {
            return _valueType;
        }

        /** Sort alphabetically by parameter name */
        @Override
        public int compareTo(@NotNull ReplacementParam o)
        {
            return _name.compareToIgnoreCase(o._name);
        }

        /** @return the formatting of the content - HTML, plaintext, etc */
        public ContentType getContentType()
        {
            return _contentType;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testPlainToPlain()
        {
            assertEquals("plain \n<>\"", ContentType.Plain.format("plain \n<>\"", ContentType.Plain));
        }

        @Test
        public void testPlainToHTML()
        {
            assertEquals("plain <br>\n&lt;&gt;&quot;", ContentType.HTML.format("plain \n<>\"", ContentType.Plain));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testHTMLToPlain()
        {
            ContentType.Plain.format("plain <>\"", ContentType.HTML);
        }

        @Test
        public void testHTMLToHTML()
        {
            assertEquals("plain <br/>&lt;&gt;&quot;", ContentType.HTML.format("plain <br/>&lt;&gt;&quot;", ContentType.HTML));
        }
    }
}
