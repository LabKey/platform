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

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ehr.security.AbstractEHRPermission;
import org.labkey.api.ehr.security.EHRDataEntryPermission;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.DataSetTable;
import org.labkey.api.util.Pair;
import org.labkey.api.view.template.ClientDependency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 4/27/13
 * Time: 10:27 AM
 */
abstract public class AbstractFormSection implements FormSection
{
    private String _name;
    private String _label;
    private String _xtype;
    private String _clientModelClass = "EHR.model.DefaultClientModel";
    private String _clientStoreClass = "EHR.data.DataEntryClientStore";
    private EHRService.FORM_SECTION_LOCATION _location = EHRService.FORM_SECTION_LOCATION.Body;
    private String _tabName = null;
    private TEMPLATE_MODE _templateMode = TEMPLATE_MODE.MULTI;

    private List<String> _configSources = new ArrayList<String>();

    private LinkedHashSet<ClientDependency> _clientDependencies = new LinkedHashSet<>();

    protected static final Logger _log = Logger.getLogger(FormSection.class);

    public AbstractFormSection(String name, String label, String xtype)
    {
        this(name, label, xtype, EHRService.FORM_SECTION_LOCATION.Body);
    }

    public AbstractFormSection(String name, String label, String xtype, EHRService.FORM_SECTION_LOCATION location)
    {
        _name = name;
        _label = label;
        _xtype = xtype;
        _location = location;

        addClientDependency(ClientDependency.fromFilePath("ehr/window/CopyFromSectionWindow.js"));
    }

    public String getName()
    {
        return _name;
    }

    public String getLabel()
    {
        return _label;
    }

    public String getXtype()
    {
        return _xtype;
    }

    public void setXtype(String xtype)
    {
        _xtype = xtype;
    }

    protected void setTabName(String tabName)
    {
        _tabName = tabName;
    }

    public EHRService.FORM_SECTION_LOCATION getLocation()
    {
        return _location;
    }

    public void setLocation(EHRService.FORM_SECTION_LOCATION location)
    {
        _location = location;
    }

    public String getClientModelClass()
    {
        return _clientModelClass;
    }

    public String getClientStoreClass()
    {
        return _clientStoreClass;
    }

    protected void setClientModelClass(String clientModelClass)
    {
        _clientModelClass = clientModelClass;
    }

    public void setClientStoreClass(String clientStoreClass)
    {
        _clientStoreClass = clientStoreClass;
    }

    public List<String> getConfigSources()
    {
        return _configSources;
    }

    public void setConfigSources(List<String> configSources)
    {
        _configSources = new ArrayList<>(configSources);
    }

    public void addConfigSource(String source)
    {
        _configSources.add(source);
    }

    public void setTemplateMode(TEMPLATE_MODE mode)
    {
        _templateMode = mode;
    }

    public static enum TEMPLATE_MODE
    {
        MULTI("TEMPLATE"),
        NO_ID("TEMPLATE_NO_ID"),
        ENCOUNTER("TEMPLATE_ENCOUNTER"),
        NONE(null);

        private String _button;

        TEMPLATE_MODE(String button)
        {
            _button = button;
        }

        public String getButton()
        {
            return _button;
        }
    }

    public boolean hasPermission(DataEntryFormContext ctx, Class<? extends Permission> perm)
    {
        for (TableInfo ti : getTables(ctx))
        {
            if (AbstractEHRPermission.class.isAssignableFrom(perm) && !(ti instanceof DataSetTable))
            {
                return ctx.getContainer().hasPermission(ctx.getUser(), EHRDataEntryPermission.class);
            }
            else
            {
                if (!ti.hasPermission(ctx.getUser(), perm))
                    return false;
            }
        }

        return true;
    }

    public Set<Pair<String, String>> getTableNames()
    {
        return new HashSet<>();
    }

    public Set<TableInfo> getTables(DataEntryFormContext ctx)
    {
        Set<TableInfo> tables = new HashSet<>();
        Set<Pair<String, String>> pairs = getTableNames();

        for (Pair<String, String> pair : pairs)
        {
            TableInfo ti = ctx.getTable(pair.first, pair.second);
            if (ti == null)
            {
                _log.error("Unable to create table: " + pair.first + "." + pair.second);
                continue;
            }

            tables.add(ti);
        }

        return tables;
    }

    public JSONObject toJSON(DataEntryFormContext ctx)
    {
        JSONObject json = new JSONObject();

        json.put("name", getName());
        json.put("label", getLabel());
        json.put("xtype", getXtype());
        json.put("clientModelClass", getClientModelClass());
        json.put("clientStoreClass", getClientStoreClass());
        json.put("location", getLocation().name());
        json.put("fieldConfigs", getFieldConfigs(ctx));
        json.put("supportsTemplates", _templateMode != TEMPLATE_MODE.NONE);
        json.put("configSources", getConfigSources());
        json.put("tbarButtons", getTbarButtons());
        json.put("tbarMoreActionButtons", getTbarMoreActionButtons());

        if (_tabName != null)
            json.put("tabName", _tabName);

        return json;
    }

    public List<String> getTbarButtons()
    {
        List<String> defaultButtons = new ArrayList<String>();
        defaultButtons.add("ADDRECORD");
        defaultButtons.add("ADDANIMALS");
        defaultButtons.add("DELETERECORD");
        defaultButtons.add("SELECTALL");

        //omit the template btn from any formtype with specialized parent->child inheritance
        List<String> sources = getConfigSources();
        if (!sources.contains("Encounter") && !sources.contains("Labwork"))
        {
            defaultButtons.add("COPYFROMSECTION");
        }

        if (_templateMode.getButton() != null)
            defaultButtons.add(_templateMode.getButton());

        return defaultButtons;
    }

    public List<String> getTbarMoreActionButtons()
    {
        List<String> defaultButtons = new ArrayList<String>();

        //omit the template btn from any formtype with specialized parent->child inheritance
        List<String> sources = getConfigSources();
        if (!sources.contains("Encounter"))
        {
            defaultButtons.add("DUPLICATE");
        }

        defaultButtons.add("BULKEDIT");
        defaultButtons.add("GUESSPROJECT");
        defaultButtons.add("REFRESH");

        return defaultButtons;
    }

    abstract protected List<FormElement> getFormElements(DataEntryFormContext ctx);

    private List<JSONObject> getFieldConfigs(DataEntryFormContext ctx)
    {
        List<JSONObject> ret = new ArrayList<>();
        for (FormElement fe : getFormElements(ctx))
        {
            ret.add(fe.toJSON(ctx.getContainer(), ctx.getUser()));
        }

        return ret;
    }

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        return _clientDependencies;
    }

    public void addClientDependency(ClientDependency cd)
    {
        _clientDependencies.add(cd);
    }
}
