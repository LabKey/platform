/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.template.ClientDependency;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 4/27/13
 * Time: 8:34 AM
 */
public interface DataEntryForm
{
    abstract public String getName();

    abstract public String getLabel();

    abstract public String getCategory();

    abstract public boolean hasPermission(Class<? extends Permission> clazz);

    /**
     * Intended for checks like testing whether an owning module is active
     */
    abstract public boolean isAvailable();

    abstract public boolean isVisible();

    abstract public String getJavascriptClass();

    abstract public JSONObject toJSON(boolean includeFormElements);

    abstract public JSONObject toJSON();

    abstract public List<FormSection> getFormSections();

    abstract public LinkedHashSet<ClientDependency> getClientDependencies();

    abstract public Set<TableInfo> getTables();

    abstract public boolean canRead();
}
