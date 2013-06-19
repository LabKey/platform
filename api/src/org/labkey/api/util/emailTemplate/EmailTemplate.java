/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;

import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
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

    private String _name;
    private String _body;
    private String _subject;
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
        _replacements.add(new ReplacementParam("organizationName", "Organization name (look and feel settings)"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getCompanyName();}
        });
        _replacements.add(new ReplacementParam("siteShortName", "Header short name"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getShortName();}
        });
        _replacements.add(new ReplacementParam("contextPath", "Web application context path"){
            public String getValue(Container c) {return AppProps.getInstance().getContextPath();}
        });
        _replacements.add(new ReplacementParam("supportLink", "Page where users can request support"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getReportAProblemPath();}
        });
        _replacements.add(new ReplacementParam("systemDescription", "Header description"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getDescription();}
        });
        _replacements.add(new ReplacementParam("systemEmail", "From address for system notification emails"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getSystemEmailAddress();}
        });
        _replacements.add(new ReplacementParam("currentDateTime", "Current date and time of the server"){
            public Object getValue(Container c) {return new Date();}
        });
        _replacements.add(new ReplacementParam("folderName", "Name of the folder that generated the email, if it is scoped to a folder"){
            public Object getValue(Container c) {return c.isRoot() ? null : c.getName();}
        });
        _replacements.add(new ReplacementParam("folderPath", "Path of the folder that generated the email, if it is scoped to a folder"){
            public Object getValue(Container c) {return c.isRoot() ? null : c.getPath();}
        });
        _replacements.add(new ReplacementParam("folderURL", "URL to the folder that generated the email, if it is scoped to a folder"){
            public Object getValue(Container c) {return c.isRoot() ? null : PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c).getURIString();}
        });
        _replacements.add(new ReplacementParam("homePageURL", "The home page of this installation"){
            public String getValue(Container c) {
                return ActionURL.getBaseServerURL();   // TODO: Use AppProps.getHomePageUrl() instead?
            }
        });
    }

    public EmailTemplate(String name)
    {
        this(name, "", "", "");
    }

    public EmailTemplate(String name, String subject, String body, String description)
    {
        _name = name;
        _subject = subject;
        _body = body;
        _description = description;
    }

    public String getName(){return _name;}
    public void setName(String name){_name = name;}
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

    private String getFormat(String paramNameAndFormat)
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
                Object value = param.getValue(c);
                if (value == null || "".equals(value))
                {
                    return "";
                }
                String format = getFormat(paramNameAndFormat);
                if (format != null)
                {
                    Formatter formatter = new Formatter();
                    formatter.format(format, value);
                    return formatter.toString();
                }
                return value.toString();
            }
        }
        return null;
    }

    public String renderSubject(Container c)
    {
        return render(c, getSubject());
    }

    public String renderBody(Container c)
    {
        return render(c, getBody());
    }

    protected String render(Container c, String text)
    {
        StringBuffer sb = new StringBuffer();
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

    public static abstract class ReplacementParam
    {
        private String _name;
        private String _description;

        public ReplacementParam(String name, String description)
        {
            _name = name;
            _description = description;
        }
        public String getName(){return _name;}
        public String getDescription(){return _description;}
        public abstract Object getValue(Container c);
    }
}
