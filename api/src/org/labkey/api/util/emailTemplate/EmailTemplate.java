/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import javax.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.MissingFormatArgumentException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Karl Lum
 * Date: Jan 15, 2007
 */
public abstract class EmailTemplate
{
    private static Pattern scriptPattern = Pattern.compile("\\^(.*?)\\^");
    private static List<ReplacementParam> _replacements = new ArrayList<>();
    private static final String FORMAT_DELIMITER = "|";

    private static final Logger LOG = Logger.getLogger(EmailTemplate.class);

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

    /** The format of the email to be generated */
    @NotNull private final ContentType _contentType;
    @NotNull private final String _name;
    private String _body;
    private String _subject;
    private String _senderName;
    private String _description;
    private int _priority = 50;
    /** Scope is the locations in which the user should be able to edit this template. It should always be the same
     * for a given subclass, regardless of the instances */
    private Scope _scope = Scope.Site;
    /**
     * Container in which this template is stored. Null for the default templates defined in code, the root
     * container for site level templates, or a specific folder.
     */
    private Container _container = null;

    static
    {
        _replacements.add(new ReplacementParam<String>("organizationName", String.class, "Organization name (look and feel settings)"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getCompanyName();}
        });
        _replacements.add(new ReplacementParam<String>("siteShortName", String.class, "Header short name"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getShortName();}
        });
        _replacements.add(new ReplacementParam<String>("contextPath", String.class, "Web application context path"){
            public String getValue(Container c) {return AppProps.getInstance().getContextPath();}
        });
        _replacements.add(new ReplacementParam<String>("supportLink", String.class, "Page where users can request support"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getReportAProblemPath();}
        });
        _replacements.add(new ReplacementParam<String>("systemDescription", String.class, "Header description"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getDescription();}
        });
        _replacements.add(new ReplacementParam<String>("systemEmail", String.class, "From address for system notification emails"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getSystemEmailAddress();}
        });
        _replacements.add(new ReplacementParam<Date>("currentDateTime", Date.class, "Current date and time of the server"){
            public Date getValue(Container c) {return new Date();}
        });
        _replacements.add(new ReplacementParam<String>("folderName", String.class, "Name of the folder that generated the email, if it is scoped to a folder"){
            public String getValue(Container c) {return c.isRoot() ? null : c.getName();}
        });
        _replacements.add(new ReplacementParam<String>("folderPath", String.class, "Full path of the folder that generated the email, if it is scoped to a folder"){
            public String getValue(Container c) {return c.isRoot() ? null : c.getPath();}
        });
        _replacements.add(new ReplacementParam<String>("folderURL", String.class, "URL to the folder that generated the email, if it is scoped to a folder"){
            public String getValue(Container c) {return c.isRoot() ? null : PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c).getURIString();}
        });
        _replacements.add(new ReplacementParam<String>("homePageURL", String.class, "The home page of this installation"){
            public String getValue(Container c) {
                return ActionURL.getBaseServerURL();   // TODO: Use AppProps.getHomePageUrl() instead?
            }
        });
    }

    public EmailTemplate(@NotNull String name)
    {
        this(name, "", "", "", ContentType.Plain);
    }

    public EmailTemplate(@NotNull String name, String subject, String body, String description)
    {
        this(name, subject, body, description, ContentType.Plain);
    }

    public EmailTemplate(@NotNull String name, String subject, String body, String description, @NotNull ContentType contentType)
    {
        this(name, subject, body, description, contentType, null);
    }

    public EmailTemplate(@NotNull String name, String subject, String body, String description, @NotNull ContentType contentType, String senderDisplayName)
    {
        _name = name;
        _subject = subject;
        _body = body;
        _description = description;
        _contentType = contentType;
        _senderName = senderDisplayName;
    }

    @NotNull public String getName(){return _name;}
    public String getSubject(){return _subject;}
    public void setSubject(String subject){_subject = subject;}
    public String getBody(){return _body;}
    public void setBody(String body){_body = body;}
    public void setPriority(int priority){_priority = priority;}
    public int getPriority(){return _priority;}
    public String getDescription(){return _description;}
    public void setDescription(String description){_description = description;}
    public Scope getEditableScopes(){return _scope;}
    public void setEditableScopes(Scope scope){_scope = scope;}
    public Container getContainer(){return _container;}
    /* package */ void setContainer(Container c){_container = c;}
    public String getSenderName(){return _senderName;}
    public void setSenderName(String senderName){_senderName = senderName;}

    @NotNull
    public ContentType getContentType()
    {
        return _contentType;
    }

    public boolean isValid(String[] error)
    {
        try {
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

    protected boolean _validate(String text) throws Exception
    {
        if (text != null)
        {
            Matcher m = scriptPattern.matcher(text);
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
        for (ReplacementParam param : getValidReplacements())
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

    public String getReplacement(Container c, String paramNameAndFormat)
    {
        String paramName = getParameterName(paramNameAndFormat);
        for (ReplacementParam param : getValidReplacements())
        {
            if (param.getName().equalsIgnoreCase(paramName))
            {
                return param.getFormattedValue(c, getTemplateFormat(paramNameAndFormat), _contentType);
            }
        }
        return null;
    }

    public String renderSubject(Container c)
    {
        return render(c, getSubject());
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

    protected String render(Container c, String text)
    {
        StringBuilder sb = new StringBuilder();
        Matcher m = scriptPattern.matcher(text);
        int start;
        int end = 0;
        while (m.find())
        {
            start = m.start();
            String value = m.group(1);
            sb.append(text.substring(end, start));
            sb.append(getReplacement(c, value));
            end = m.end();
        }
        sb.append(text.substring(end));
        return sb.toString();
    }

    public List<ReplacementParam> getValidReplacements()
    {
        return _replacements;
    }

    public static abstract class ReplacementParam<Type> implements Comparable<ReplacementParam<Type>>
    {
        @NotNull
        private final String _name;
        private final Class<Type> _valueType;
        private final String _description;
        private final ContentType _contentType;

        public ReplacementParam(@NotNull String name, Class<Type> valueType, String description)
        {
            this(name, valueType, description, ContentType.Plain);
        }

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
                    // this for String values. That is the overwhelming majority of values, and the non-strings
                    // are Dates which are unlikely to be rendered using a format that needs encoding.
                    value = emailFormat.format((String) value, getContentType());
                }

                if (templateFormat != null)
                {
                    try
                    {
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
                    formattedValue = DateUtil.formatDateTime(c, (Date)value);
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
        public int compareTo(ReplacementParam o)
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
        public void testHMLToPlain()
        {
            ContentType.Plain.format("plain <>\"", ContentType.HTML);
        }

        @Test
        public void testHMLToHTML()
        {
            assertEquals("plain <br/>&lt;&gt;&quot;", ContentType.HTML.format("plain <br/>&lt;&gt;&quot;", ContentType.HTML));
        }
    }
}
