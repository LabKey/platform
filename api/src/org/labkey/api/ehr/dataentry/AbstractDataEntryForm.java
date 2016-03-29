/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.Dataset;
import org.labkey.api.util.Pair;
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
    private DataEntryFormContext _ctx;
    private String _name;
    private String _label;
    private String _category;
    private String _javascriptClass = "EHR.panel.DataEntryPanel";
    private String _storeCollectionClass = "EHR.data.StoreCollection";
    private List<FormSection> _sections;
    private LinkedHashSet<ClientDependency> _clientDependencies = new LinkedHashSet<>();
    private AbstractFormSection.TEMPLATE_MODE _templateMode = AbstractFormSection.TEMPLATE_MODE.MULTI;
    private Module _owner;
    private boolean _displayReviewRequired = false;

    public AbstractDataEntryForm(DataEntryFormContext ctx, Module owner, String name, String label, String category, List<FormSection> sections)
    {
        _ctx = ctx;
        _owner = owner;
        _name = name;
        _label = label;
        _category = category;
        _sections = new ArrayList<>(sections);
    }

    public String getName()
    {
        return _name;
    }

    public String getLabel()
    {
        return _label;
    }

    protected void addSection(FormSection s)
    {
        _sections.add(s);
    }

    protected void setDisplayReviewRequired(boolean displayReviewRequired)
    {
        _displayReviewRequired = displayReviewRequired;
    }

    protected void setLabel(String label)
    {
        _label = label;
    }

    protected DataEntryFormContext getCtx()
    {
        return _ctx;
    }

    public List<FormSection> getFormSections()
    {
        return Collections.unmodifiableList(_sections);
    }

    public String getCategory()
    {
        return _category;
    }

    public boolean hasPermission(Class<? extends Permission> clazz)
    {
        for (FormSection section : getFormSections())
        {
            if (!section.hasPermission(_ctx, clazz))
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

    public boolean isAvailable()
    {
        if (!_ctx.getContainer().getActiveModules().contains(_owner))
            return false;

        return true;
    }

    public JSONObject toJSON()
    {
        return toJSON(true);
    }

    public JSONObject toJSON(boolean includeFormElements)
    {
        JSONObject json = new JSONObject();

        json.put("name", getName());
        json.put("label", getLabel());
        json.put("category", getCategory());
        json.put("javascriptClass", getJavascriptClass());
        json.put("storeCollectionClass", getStoreCollectionClass());
        json.put("isAvailable", isAvailable());
        json.put("isVisible", isVisible());

        JSONArray sections = new JSONArray();
        for (FormSection section : getFormSections())
        {
            sections.put(section.toJSON(_ctx, includeFormElements));
        }
        json.put("sections", sections);
        json.put("permissions", getPermissionMap());
        json.put("buttons", getButtonConfigs());
        json.put("moreActionButtons", getMoreActionButtonConfigs());
        json.put("canInsert", canInsert());
        json.put("defaultAssignedTo", getDefaultAssignedTo());
        json.put("defaultReviewRequiredPrincipal", getDefaultReviewRequiredPrincipal());

        return json;
    }

    protected Integer getDefaultAssignedTo()
    {
        return _ctx.getUser().getUserId();
    }

    protected Integer getDefaultReviewRequiredPrincipal()
    {
        ModuleProperty prop = ModuleLoader.getInstance().getModule("ehr").getModuleProperties().get("EHRSubmitForReviewPrincipal");
        String stringVal = prop.getEffectiveValue(_ctx.getContainer());
        if (stringVal != null)
        {
            UserPrincipal up = SecurityManager.getPrincipal(stringVal, _ctx.getContainer(), true);
            if (up != null)
                return up.getUserId();
        }

        return null;
    }

    protected boolean canInsert()
    {
        boolean canInsert = true;
        for (FormSection section : getFormSections())
        {
            for (Class<? extends Permission> clazz : getAvailabilityPermissions())
            {
                if (!section.hasPermission(_ctx, clazz))
                {
                    canInsert = false;
                    break;
                }
            }
        }

        return canInsert;
    }

    /**
     * Typically let normal table-level security handle read.  This can be set false to complete turn off the ability to see details on some forms.
     */
    public boolean canRead()
    {
        return true;
    }

    protected List<String> getButtonConfigs()
    {
        List<String> defaultButtons = new ArrayList<String>();
        defaultButtons.add("SAVEDRAFT");
        defaultButtons.add("CLOSE");

        if (_displayReviewRequired)
            defaultButtons.add("REVIEW");

        defaultButtons.add("SUBMIT");

        return defaultButtons;
    }

    protected void setTemplateMode(AbstractFormSection.TEMPLATE_MODE templateMode)
    {
        _templateMode = templateMode;
    }

    protected List<String> getMoreActionButtonConfigs()
    {
        List<String> defaultButtons = new ArrayList<String>();
        defaultButtons.add("VALIDATEALL");

        if (_templateMode.getFormBtn() != null)
            defaultButtons.add(_templateMode.getFormBtn());

        if (!_displayReviewRequired)
            defaultButtons.add("REVIEW");

        defaultButtons.add("SUBMITANDNEXT");
        defaultButtons.add("FORCESUBMIT");
        defaultButtons.add("DISCARD");

        return defaultButtons;
    }

    private Map<String, Map<String, Map<String, String>>> getPermissionMap()
    {
        Map<String, Map<String, Map<String, String>>> permissionMap = new HashMap<>();
        Map<String, Dataset> datasetMap = getCtx().getDatasetMap();

        for (Pair<String, String> pair : getTableNames())
        {
            String schemaName= pair.first;
            String queryName = pair.second;

            Map<String, Map<String, String>> schemaPerms = permissionMap.get(schemaName);
            if (schemaPerms == null)
                schemaPerms = new HashMap<>();

            Map<String, String> queryPerms = schemaPerms.get(queryName);
            if (queryPerms == null)
                queryPerms = new HashMap<>();

            SecurityPolicy policy = null;

            //test if this is a dataset
            if ("study".equalsIgnoreCase(schemaName) && datasetMap.get(queryName) != null)
            {
                policy = SecurityPolicyManager.getPolicy(datasetMap.get(queryName));
            }
            else
            {
                policy = SecurityPolicyManager.getPolicy(_ctx.getContainer());
            }

            if (policy != null)
            {
                for (Class<? extends Permission> p : policy.getPermissions(_ctx.getUser()))
                {
                    queryPerms.put(p.getName(), p.getCanonicalName());
                }
            }

            schemaPerms.put(queryName, queryPerms);
            permissionMap.put(schemaName, schemaPerms);
        }

        return permissionMap;
    }

    public Set<TableInfo> getTables()
    {
        Set<TableInfo> tables = new HashSet<>();
        for (FormSection section : getFormSections())
        {
            tables.addAll(section.getTables(_ctx));
        }
        return tables;
    }

    public Set<Pair<String, String>> getTableNames()
    {
        Set<Pair<String, String>> tables = new HashSet<>();
        for (FormSection section : getFormSections())
        {
            tables.addAll(section.getTableNames());
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
    public boolean isVisible()
    {
        return true;
    }
}
