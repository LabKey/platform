/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelAppProps;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Karl Lum
 * Date: Jan 15, 2007
 */
public abstract class EmailTemplate
{
    private static Pattern scriptPattern = Pattern.compile("%(.*?)%");
    private static List<ReplacementParam> _replacements = new ArrayList<ReplacementParam>();

    private String _name;
    private String _body;
    private String _subject;
    private String _description;
    private int _priority = 50;

    static
    {
        _replacements.add(new ReplacementParam("organizationName", "Organization name (look and feel settings)"){
            public String getValue(Container c) {return LookAndFeelAppProps.getInstance(c).getCompanyName();}
        });
        _replacements.add(new ReplacementParam("siteShortName", "Header short name"){
            public String getValue(Container c) {return LookAndFeelAppProps.getInstance(c).getSystemShortName();}
        });
        _replacements.add(new ReplacementParam("contextPath", "Web application context path"){
            public String getValue(Container c) {return AppProps.getInstance().getContextPath();}
        });
        _replacements.add(new ReplacementParam("supportLink", "Page where users can request support"){
            public String getValue(Container c) {return LookAndFeelAppProps.getInstance(c).getReportAProblemPath();}
        });
        _replacements.add(new ReplacementParam("systemDescription", "Header description"){
            public String getValue(Container c) {return LookAndFeelAppProps.getInstance(c).getSystemDescription();}
        });
        _replacements.add(new ReplacementParam("systemEmail", "From address for system notification emails"){
            public String getValue(Container c) {return LookAndFeelAppProps.getInstance(c).getSystemEmailAddress();}
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

    protected boolean isValidReplacement(String value)
    {
        for (ReplacementParam param : getValidReplacements())
        {
            if (param.getName().equals(value))
                return true;
        }
        return false;
    }

    public String getReplacement(Container c, String value)
    {
        for (ReplacementParam param : getValidReplacements())
        {
            if (param.getName().equals(value))
                return param.getValue(c);
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
        public abstract String getValue(Container c);
    }
}
