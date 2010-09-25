/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.api.exp.property;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;

import java.util.Map;

public interface Domain extends IPropertyType
{
    Container getContainer();
    DomainKind getDomainKind();
    String getName();
    String getDescription();
    int getTypeId();
    String getTypeURI();

    Container[] getInstanceContainers();
    Container[] getInstanceContainers(User user, Class<? extends Permission> perm);

    void setDescription(String description);
    void setPropertyIndex(DomainProperty prop, int index);
    DomainProperty[] getProperties();
    DomainProperty getProperty(int id);
    DomainProperty getPropertyByName(String name);
    ActionURL urlEditDefinition(boolean allowFileLinkProperties, boolean allowAttachmentProperties, boolean showDefaultValueSettings);
    ActionURL urlShowData();

    DomainProperty addProperty();

    ColumnInfo[] getColumns(TableInfo sourceTable, ColumnInfo lsidColumn, User user);

    void delete(User user) throws DomainNotFoundException;
    void save(User user) throws ChangePropertyDescriptorException;

    /**
     * This returns a map of names -> PropertyDescriptor that is useful for import that includes all of the
     * different names that a column may be referred to, dealing with naming collisions between aliases and property names
     * in the right way.
     * @param includeMVIndicators whether or not to include the missing value indicator "column" names in the map
     */
    Map<String, DomainProperty> createImportMap(boolean includeMVIndicators);
}
