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
import org.labkey.api.ehr.security.EHRDataEntryPermission;
import org.labkey.api.module.Module;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.DataSetTable;
import org.labkey.api.view.template.ClientDependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
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
    private String _storeCollectionClass = "EHR.data.StoreCollection";
    private List<FormSection> _sections;
    private LinkedHashSet<ClientDependency> _clientDependencies = new LinkedHashSet<>();
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

    protected void setLabel(String label)
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

    public boolean hasPermission(Container c, User u, Class<? extends Permission> clazz)
    {
        for (FormSection section : getFormSections())
        {
            if (!section.hasPermission(c, u, clazz))
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

    public String getStoreCollectionClass()
    {
        return _storeCollectionClass;
    }

    protected void setStoreCollectionClass(String storeCollectionClass)
    {
        _storeCollectionClass = storeCollectionClass;
    }

    protected List<Class<? extends Permission>> getAvailabilityPermissions()
    {
        return Collections.<Class<? extends Permission>>singletonList(InsertPermission.class);
    }

    public boolean isAvailable(Container c, User u)
    {
        if (!c.getActiveModules().contains(_owner))
            return false;

        return true;
    }

    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = new JSONObject();

        json.put("name", getName());
        json.put("label", getLabel());
        json.put("category", getCategory());
        json.put("javascriptClass", getJavascriptClass());
        json.put("storeCollectionClass", getStoreCollectionClass());
        json.put("isAvailable", isAvailable(c, u));
        json.put("isVisible", isVisible(c, u));

        JSONArray sections = new JSONArray();
        for (FormSection section : getFormSections())
        {
            sections.put(section.toJSON(c, u));
        }
        json.put("sections", sections);
        json.put("permissions", getPermissionMap(c, u));
        json.put("buttons", getButtonConfigs());
        json.put("moreActionButtons", getMoreActionButtonConfigs());

        boolean canInsert = true;
        for (FormSection section : getFormSections())
        {
            for (Class<? extends Permission> clazz : getAvailabilityPermissions())
            {
                if (!section.hasPermission(c, u, clazz))
                {
                    canInsert = false;
                    break;
                }
            }
        }
        json.put("canInsert", canInsert);

        return json;
    }

    protected List<String> getButtonConfigs()
    {
        List<String> defaultButtons = new ArrayList<String>();
        defaultButtons.add("SAVEDRAFT");
        defaultButtons.add("CLOSE");
        defaultButtons.add("SUBMIT");

        return defaultButtons;
    }

    protected List<String> getMoreActionButtonConfigs()
    {
        List<String> defaultButtons = new ArrayList<String>();
        defaultButtons.add("VALIDATEALL");
        defaultButtons.add("REVIEW");
        defaultButtons.add("FORCESUBMIT");
        defaultButtons.add("DISCARD");

        return defaultButtons;
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

            SecurityPolicy policy = null;
            if (ti instanceof DataSetTable)
            {
                DataSetTable ds = (DataSetTable)ti;
                policy = SecurityPolicyManager.getPolicy(ds.getDataSet());
            }
            else
            {
                policy = SecurityPolicyManager.getPolicy(c);
            }

            if (policy != null)
            {
                for (Class<? extends Permission> p : policy.getPermissions(u))
                {
                    queryPerms.put(p.getName(), p.getCanonicalName());
                }
            }

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
        cds.addAll(_clientDependencies);

        for (FormSection section : getFormSections())
        {
            cds.addAll(section.getClientDependencies());
        }
        return cds;
    }

    protected void addClientDependency(ClientDependency cd)
    {
        _clientDependencies.add(cd);
    }

    @Override
    public boolean isVisible(Container c, User u)
    {
        return true;
    }
}
