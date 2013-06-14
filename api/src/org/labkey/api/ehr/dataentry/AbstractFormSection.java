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
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
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
 * Created with IntelliJ IDEA.
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
    private List<String> _configSources = new ArrayList<String>();

    private LinkedHashSet<ClientDependency> _clientDependencies = new LinkedHashSet<ClientDependency>();

    protected static final Logger _log = Logger.getLogger(FormSection.class);

    public AbstractFormSection(String name, String label, String xtype)
    {
        _name = name;
        _label = label;
        _xtype = xtype;
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

    public String getClientModelClass()
    {
        return _clientModelClass;
    }

    public List<String> getConfigSources()
    {
        return _configSources;
    }

    public void setConfigSources(List<String> configSources)
    {
        _configSources = new ArrayList<String>(configSources);
    }

    public void addConfigSource(String source)
    {
        _configSources.add(source);
    }

    public boolean hasPermission(Container c, User u, Class<? extends Permission> perm)
    {
        for (TableInfo ti : getTables(c, u))
        {
            if (!ti.hasPermission(u, perm))
                return false;
        }

        return true;
    }

    public Set<Pair<String, String>> getTableNames()
    {
        return new HashSet<Pair<String, String>>();
    }

    public Set<TableInfo> getTables(Container c, User u)
    {
        Set<TableInfo> tables = new HashSet<TableInfo>();
        Map<String, UserSchema> schemas = new HashMap<String, UserSchema>();

        Set<Pair<String, String>> pairs = getTableNames();

        for (Pair<String, String> pair : pairs)
        {
            UserSchema us = schemas.containsKey(pair.first) ? schemas.get(pair.first) : QueryService.get().getUserSchema(u, c, pair.first);
            if (us == null)
            {
                _log.error("Unable to create schema: " + pair.second);
                continue;
            }

            schemas.put(pair.first, us);

            TableInfo ti = us.getTable(pair.second);
            if (ti == null)
            {
                _log.error("Unable to create table: " + pair.first + "." + pair.second);
                continue;
            }

            tables.add(ti);
        }

        return tables;
    }

    public List<JSONObject> getStoreConfigs(Container c, User u)
    {
        List<JSONObject> stores = new ArrayList<JSONObject>();
        for (TableInfo ti : getTables(c, u))
        {
            JSONObject json = new JSONObject();
            json.put("schemaName", ti.getPublicSchemaName());
            json.put("queryName", ti.getPublicName());

            stores.add(json);
        }

        return stores;
    }

    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = new JSONObject();

        json.put("name", getName());
        json.put("label", getLabel());
        json.put("xtype", getXtype());
        json.put("clientModelClass", getClientModelClass());
        json.put("storeConfigs", getStoreConfigs(c, u));
        json.put("fieldConfigs", getFieldConfigs(c, u));
        json.put("configSources", getConfigSources());

        return json;
    }

    abstract protected List<FormElement> getFormElements(Container c, User u);

    private List<JSONObject> getFieldConfigs(Container c, User u)
    {
        List<JSONObject> ret = new ArrayList<JSONObject>();
        for (FormElement fe : getFormElements(c, u))
        {
            ret.add(fe.toJSON(c, u));
        }

        return ret;
    }

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        return _clientDependencies;
    }

    protected void addClientDependency(ClientDependency cd)
    {
        _clientDependencies.add(cd);
    }
}
