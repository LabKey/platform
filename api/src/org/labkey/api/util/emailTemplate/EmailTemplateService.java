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

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertySchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.NotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages admin-customized email templates by persisting in the property store.
 *
 * User: Karl Lum
 * Date: Jan 15, 2007
 */
public class EmailTemplateService
{
    private static final Logger _log = LogHelper.getLogger(EmailTemplateService.class, "Email template registration and migration");

    private static final String EMAIL_TEMPLATE_PROPERTIES_MAP_NAME = "emailTemplateProperties";
    private static final String MESSAGE_SUBJECT_PART = "subject";
    private static final String MESSAGE_BODY_PART = "body";
    /** Sender display name */
    private static final String MESSAGE_FROM_PART = "from";
    /** Reply to email address */
    private static final String MESSAGE_REPLY_TO_PART = "replyToEmail";
    private static final String EMAIL_TEMPLATE_DELIM = "/";

    private static final EmailTemplateService instance = new EmailTemplateService();
    private final Set<Class<? extends EmailTemplate>> _templates = new LinkedHashSet<>();

    public static EmailTemplateService get()
    {
        return instance;
    }
    private EmailTemplateService(){}

    /** Adds an entry to the list of known and customizable templates */
    public void registerTemplate(Class<? extends EmailTemplate> templateClass)
    {
        synchronized(_templates)
        {
            _log.debug("Registering email template " + templateClass.getName());
            if (_templates.contains(templateClass))
                throw new IllegalStateException("Template : " + templateClass.getName() + " has previously been registered.");

            if (!EmailTemplate.class.isAssignableFrom(templateClass))
                throw new IllegalArgumentException("The specified class: " + templateClass + " is not an instance of EmailTemplate");

            _templates.add(templateClass);
        }
    }

    /** Looks only at site-level and default templates */
    public <T extends EmailTemplate> T getEmailTemplate(Class<T> templateClass)
    {
        return getEmailTemplate(templateClass, ContainerManager.getRoot());
    }

    /** Looks at folder-level, site-level and default templates */
    public <T extends EmailTemplate> T getEmailTemplate(Class<T> templateClass, Container c)
    {
        //noinspection unchecked
        return (T)_getEmailTemplates(c).get(templateClass);
    }

    public List<EmailTemplate> getEditableEmailTemplates(Container c)
    {
        List<EmailTemplate> templates = new ArrayList<>(_getEmailTemplates(c).values());

        if (!c.isRoot())
        {
            templates.removeIf(emailTemplate -> !emailTemplate.getEditableScope().isEditableIn(c));
        }

        templates.sort((o1, o2) ->
        {
            if (o1 == null && o2 == null) return 0;
            if (o1 == null) return 1;
            if (o2 == null) return -1;

            return o1.getName().compareToIgnoreCase(o2.getName());
        });

        return templates;
    }

    private PropertyManager.PropertyMap getProperties(Container c, boolean writable)
    {
        if (writable)
            return PropertyManager.getWritableProperties(c, EMAIL_TEMPLATE_PROPERTIES_MAP_NAME, true);
        else
            return PropertyManager.getProperties(c, EMAIL_TEMPLATE_PROPERTIES_MAP_NAME);
    }

    private Map<Class<? extends EmailTemplate>, EmailTemplate> _getEmailTemplates(Container c)
    {
        Map<Class<? extends EmailTemplate>, EmailTemplate> templates = new HashMap<>();
        // Populate map in override sequence, so that the most specific override will be used

        // First, the default templates
        for (Class<? extends EmailTemplate> et : _templates)
        {
            templates.put(et, createTemplate(et));
        }

        // Second, the site-wide templates stored in the database
        addTemplates(templates, ContainerManager.getRoot());

        // Finally, the folder-scoped templates stored in the database
        addTemplates(templates, c);

        return templates;
    }

    private void addTemplates(Map<Class<? extends EmailTemplate>, EmailTemplate> templates, Container c)
    {
        Map<String, String> map = getProperties(c, false);
        for (Map.Entry<String, String> entry : map.entrySet())
        {
            final String key = entry.getKey();

            // Key format is "<TEMPLATE_CLASS_NAME>/<subject or body>"
            String[] parts = key.split(EMAIL_TEMPLATE_DELIM);

            if (parts.length == 2)
            {
                try
                {
                    String className = parts[0];
                    String partType = parts[1];
                    EmailTemplate et = templates.get(getTemplateClass(className));
                    if (et == null)
                    {
                        et = createTemplate(className);
                        templates.put(et.getClass(), et);
                    }
                    et.setContainer(c);

                    // Subject and bodies are stored as two separate key-value pairs in the map
                    if (MESSAGE_SUBJECT_PART.equals(partType))
                        et.setSubject(entry.getValue());
                    else if (MESSAGE_BODY_PART.equals(partType))
                        et.setBody(entry.getValue());
                    else if (MESSAGE_FROM_PART.equals(partType))
                        et.setSenderName((entry.getValue()));
                    else if (MESSAGE_REPLY_TO_PART.equals(partType))
                        et.setReplyToEmail((entry.getValue()));
                }
                // do nothing, we don't necessarily care about stale template properties
                catch (Exception e)
                {
                    //_log.error("Unable to create a template for: " + parts[1], e);
                }
            }
        }
    }

    private Class<? extends EmailTemplate> getTemplateClass(String className)
    {
        try
        {
            Class<?> c = Class.forName(className);
            if (EmailTemplate.class.isAssignableFrom(c))
            {
                //noinspection unchecked
                return (Class<? extends EmailTemplate>)c;
            }
            throw new IllegalArgumentException("The specified class: " + c.getName() + " is not an instance of EmailTemplate");
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("The specified class could not be found: " + className);
        }

    }

    public EmailTemplate createTemplate(String className)
    {
        return createTemplate(getTemplateClass(className));
    }

    public EmailTemplate createTemplate(Class<? extends EmailTemplate> templateClass)
    {
        try
        {
            return templateClass.newInstance();
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("The specified class could not be created: " + templateClass.getName(), e);
        }
    }

    public void saveEmailTemplate(EmailTemplate template, Container c)
    {
        if (!template.getEditableScope().isEditableIn(c))
        {
            throw new NotFoundException("Cannot save template " + c + " in " + c);
        }
        PropertyManager.PropertyMap map = getProperties(c, true);

        final String className = template.getClass().getName();
        map.put(className + EMAIL_TEMPLATE_DELIM + MESSAGE_SUBJECT_PART, template.getSubject());
        map.put(className + EMAIL_TEMPLATE_DELIM + MESSAGE_BODY_PART, template.getBody());
        map.put(className + EMAIL_TEMPLATE_DELIM + MESSAGE_FROM_PART, template.getSenderName());
        map.put(className + EMAIL_TEMPLATE_DELIM + MESSAGE_REPLY_TO_PART, template.getReplyToEmail());
        map.save();
    }

    public void deleteEmailTemplate(EmailTemplate template, Container c)
    {
        PropertyManager.PropertyMap map = getProperties(c, true);

        final String className = template.getClass().getName();
        map.remove(className + EMAIL_TEMPLATE_DELIM + MESSAGE_SUBJECT_PART);
        map.remove(className + EMAIL_TEMPLATE_DELIM + MESSAGE_BODY_PART);
        map.remove(className + EMAIL_TEMPLATE_DELIM + MESSAGE_FROM_PART);
        map.remove(className + EMAIL_TEMPLATE_DELIM + MESSAGE_REPLY_TO_PART);
        map.save();
    }

    /**
     * Moves all properties associated with an email template to a new package+class. Useful when moving and/or renaming
     * an email template class. Expected to be called from upgrade code, hence no cache clearing.
     * @param oldClassName Old full package and class name
     * @param newClassName New full package and class name
     */
    public void relocateEmailTemplateProperties(String oldClassName, String newClassName)
    {
        TableInfo tinfo = PropertySchema.getInstance().getTableInfoProperties();
        SQLFragment sql = new SQLFragment("UPDATE ").append(tinfo).append(" SET Name = REPLACE(Name, ?, ?) WHERE Name LIKE ?");
        sql.add(oldClassName);
        sql.add(newClassName);
        sql.add(oldClassName + "%");

        int updated = new SqlExecutor(tinfo.getSchema()).execute(sql);
        _log.info("Migrated " + StringUtilsLabKey.pluralize(updated, "template property", "template properties") + " from " + oldClassName + " to " + newClassName);
    }
}