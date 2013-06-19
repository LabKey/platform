/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.ehr.dataentry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.template.ClientDependency;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 4/27/13
 * Time: 8:38 AM
 */
public class AbstractDataEntryForm implements DataEntryForm
{
    private String _name;
    private String _label;
    private String _category;
    private String _javascriptClass = "EHR.panel.DataEntryPanel";
    private List<FormSection> _sections;
    private Module _owner;

    public AbstractDataEntryForm(Module owner, String name, String label, String category, List<FormSection> sections)
    {
        _owner = owner;
        _name = name;
        _label = label;
        _category = category;
        _sections = sections;
    }

    public String getName()
    {
        return _name;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public List<FormSection> getFormSections()
    {
        return Collections.unmodifiableList(_sections);
    }

    public String getCategory()
    {
        return _category;
    }

    public boolean hasPermission(Container c, User u)
    {
        for (FormSection section : getFormSections())
        {
            if (!section.hasPermission(c, u, UpdatePermission.class))
                return false;
        }

        return true;
    }

    public String getJavascriptClass()
    {
        return _javascriptClass;
    }

    public void setJavascriptClass(String javascriptClass)
    {
        _javascriptClass = javascriptClass;
    }

    public boolean isAvailable(Container c, User u)
    {
        if (!c.getActiveModules().contains(_owner))
            return false;

        for (FormSection section : getFormSections())
        {
            if (!section.hasPermission(c, u, UpdatePermission.class))
                return false;
        }

        return true;
    }

    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = new JSONObject();

        json.put("name", getName());
        json.put("label", getLabel());
        json.put("category", getCategory());
        json.put("javascriptClass", getJavascriptClass());
        json.put("isAvailable", isAvailable(c, u));

        JSONArray sections = new JSONArray();
        for (FormSection section : getFormSections())
        {
            sections.put(section.toJSON(c, u));
        }
        json.put("sections", sections);
        json.put("permissions", getPermissionMap(c, u));

        //TODO:
        //json.put("buttons", null);

        return json;
    }

    private Map<String, Map<String, Map<String, String>>> getPermissionMap(Container c, User u)
    {
        Map<String, Map<String, Map<String, String>>> permissionMap = new HashMap<>();
        for (TableInfo ti : getTables(c, u))
        {
            String schemaName= ti.getPublicSchemaName();
            String queryName = ti.getPublicName();

            Map<String, Map<String, String>> schemaPerms = permissionMap.get(schemaName);
            if (schemaPerms == null)
                schemaPerms = new HashMap<>();

            Map<String, String> queryPerms = schemaPerms.get(queryName);
            if (queryPerms == null)
                queryPerms = new HashMap<>();

            ti.hasPermission(u, ReadPermission.class);

            schemaPerms.put(queryName, queryPerms);
            permissionMap.put(schemaName, schemaPerms);
        }

        return permissionMap;
    }

    public Set<TableInfo> getTables(Container c, User u)
    {
        Set<TableInfo> tables = new HashSet<>();
        for (FormSection section : getFormSections())
        {
            tables.addAll(section.getTables(c, u));
        }
        return tables;
    }

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> cds = new LinkedHashSet<>();
        for (FormSection section : getFormSections())
        {
            cds.addAll(section.getClientDependencies());
        }
        return cds;
    }
}
