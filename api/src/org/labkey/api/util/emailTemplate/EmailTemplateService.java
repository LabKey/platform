/*
 * Copyright (c) 2007 LabKey Corporation
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
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.UserManager;

import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 15, 2007
 */
public class EmailTemplateService
{
    static Logger _log = Logger.getLogger(EmailTemplateService.class);

    private static final String EMAIL_TEMPLATE_PROPERTY = "emailTemplateProperty";
    private static final String MESSAGE_SUBJECT_PART = "subject";
    private static final String MESSAGE_BODY_PART = "body";
    private static final String EMAIL_TEMPLATE_DELIM = "/";

    private static final EmailTemplateService instance = new EmailTemplateService();
    private Set<Class> _templates = new LinkedHashSet<Class>();

    public static EmailTemplateService get()
    {
        return instance;
    }
    private EmailTemplateService(){}

    public void registerTemplate(Class templateClass)
    {
        synchronized(_templates)
        {
            if (_templates.contains(templateClass))
                throw new IllegalStateException("Template : " + templateClass.getName() + " has previously been registered.");

            if (!EmailTemplate.class.isAssignableFrom(templateClass))
                throw new IllegalArgumentException("The specified class: " + templateClass + " is not an instance of EmailTemplate");

            _templates.add(templateClass);
        }
    }

    public EmailTemplate getEmailTemplate(String templateClassName)
    {
        return _getEmailTemplates().get(templateClassName);
    }

    public EmailTemplate[] getEmailTemplates()
    {
        EmailTemplate[] templates = _getEmailTemplates().values().toArray(new EmailTemplate[0]);

        Arrays.sort(templates, new Comparator<EmailTemplate>(){

            public int compare(EmailTemplate o1, EmailTemplate o2)
            {
                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return 1;
                if (o2 == null) return -1;

                return o1.getPriority() - o2.getPriority();
            }
        });

        return templates;
    }

    private Map<String, EmailTemplate> _getEmailTemplates()
    {
        Map<String, EmailTemplate> templates = new HashMap<String, EmailTemplate>();
        Map<String, String> map = UserManager.getUserPreferences(false);

        if (map != null)
        {
            for (Map.Entry<String, String> entry : map.entrySet())
            {
                final String key = entry.getKey();
                if (key.startsWith(EMAIL_TEMPLATE_PROPERTY))
                {
                    String[] parts = key.split(EMAIL_TEMPLATE_DELIM);
                    if (parts.length == 3)
                    {
                        EmailTemplate et = templates.get(parts[1]);
                        try {
                            if (et == null)
                            {
                                et = createTemplate(parts[1]);
                                templates.put(parts[1], et);
                            }
                            if (MESSAGE_SUBJECT_PART.equals(parts[2]))
                                et.setSubject(entry.getValue());
                            else
                                et.setBody(entry.getValue());
                        }
                        // do nothing, we don't necessarily care about stale template properties
                        catch (Exception e)
                        {
                            //_log.error("Unable to create a template for: " + parts[1], e);
                        }
                    }
                }
            }
        }

        for (Class et : _templates)
        {
            if (!templates.containsKey(et.getName()))
            {
                templates.put(et.getName(), createTemplate(et));
            }
        }
        return templates;
    }

    public EmailTemplate createTemplate(String templateClass)
    {
        try {
            return createTemplate(Class.forName(templateClass));
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("The specified class could not be found: " + templateClass);
        }
    }

    public EmailTemplate createTemplate(Class templateClass)
    {
        try {
            if (EmailTemplate.class.isAssignableFrom(templateClass))
            {
                return (EmailTemplate)templateClass.newInstance();
            }
            throw new IllegalArgumentException("The specified class: " + templateClass.getName() + " is not an instance of EmailTemplate");
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("The specified class could not be created: " + templateClass.getName());
        }
    }

    public void saveEmailTemplate(EmailTemplate template) throws SQLException {
        Map<String, String> map = UserManager.getUserPreferences(true);

        final String className = template.getClass().getName();
        map.put(EMAIL_TEMPLATE_PROPERTY + EMAIL_TEMPLATE_DELIM + className + EMAIL_TEMPLATE_DELIM + MESSAGE_SUBJECT_PART,
                template.getSubject());
        map.put(EMAIL_TEMPLATE_PROPERTY + EMAIL_TEMPLATE_DELIM + className + EMAIL_TEMPLATE_DELIM + MESSAGE_BODY_PART,
                template.getBody());
        PropertyManager.saveProperties(map);
    }

    public void deleteEmailTemplate(EmailTemplate template) throws SQLException
    {
        Map<String, String> map = UserManager.getUserPreferences(true);

        final String className = template.getClass().getName();
        map.remove(EMAIL_TEMPLATE_PROPERTY + EMAIL_TEMPLATE_DELIM + className + EMAIL_TEMPLATE_DELIM + MESSAGE_SUBJECT_PART);
        map.remove(EMAIL_TEMPLATE_PROPERTY + EMAIL_TEMPLATE_DELIM + className + EMAIL_TEMPLATE_DELIM + MESSAGE_BODY_PART);
        PropertyManager.saveProperties(map);
    }
}
