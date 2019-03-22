/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Domain extends IPropertyType
{
    Object get_Ts();
    Container getContainer();
    DomainKind getDomainKind();
    String getName();
    String getDescription();
    int getTypeId();
    String getTypeURI();

    Set<Container> getInstanceContainers();
    Set<Container> getInstanceContainers(User user, Class<? extends Permission> perm);

    void setDescription(String description);
    void setPropertyIndex(DomainProperty prop, int index);
    @NotNull
    List<? extends DomainProperty> getProperties();
    List<DomainProperty> getNonBaseProperties();
    Set<DomainProperty> getBaseProperties();
    DomainProperty getProperty(int id);
    @Nullable
    DomainProperty getPropertyByURI(String propertyURI);
    DomainProperty getPropertyByName(String name);
    ActionURL urlShowData(ContainerUser context);

    DomainProperty addPropertyOfPropertyDescriptor(PropertyDescriptor pd);
    DomainProperty addProperty();
    DomainProperty addProperty(PropertyStorageSpec spec);

    List<ColumnInfo> getColumns(TableInfo sourceTable, ColumnInfo lsidColumn, Container container, User user);

    void delete(@Nullable User user) throws DomainNotFoundException;
    void save(User user) throws ChangePropertyDescriptorException;
    void save(User user, boolean allowAddBaseProperty) throws ChangePropertyDescriptorException;

    /**
     * This returns a map of names -> PropertyDescriptor that is useful for import that includes all of the
     * different names that a column may be referred to, dealing with naming collisions between aliases and property names
     * in the right way.
     * @param includeMVIndicators whether or not to include the missing value indicator "column" names in the map
     */
    Map<String, DomainProperty> createImportMap(boolean includeMVIndicators);

    /** only used by storage provisioner */
    @Nullable   // null if not provisioned
    String getStorageTableName();
    void setEnforceStorageProperties(boolean enforceStorageProperties);

    /**
     * To generate foreign keys in a provision table, we need container-specific info
     */
    void setPropertyForeignKeys(Set<PropertyStorageSpec.ForeignKey> foreignKeys);
    Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys();

    /**
     * Used by storage provisioner to add indices to the provisioned table.  The indices on this Domain
     * are in addition to those from the {@link DomainKind#getPropertyIndices()}.
     * Currently, the indices are not saved as a part of the domain definition.
     */
    void setPropertyIndices(@NotNull Set<PropertyStorageSpec.Index> indices);
    @NotNull Set<PropertyStorageSpec.Index> getPropertyIndices();

    /**
     *
     * @param shouldDeleteAllData Flag that all data should be deleted, initial use case is for Lists and Datasets
     *                            having all their user-editable fields replaced via Import Fields form
     */
    void setShouldDeleteAllData(boolean shouldDeleteAllData);
    boolean isShouldDeleteAllData();
    boolean isProvisioned();

    @Nullable
    TemplateInfo getTemplateInfo();
}
