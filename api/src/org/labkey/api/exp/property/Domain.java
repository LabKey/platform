/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

public interface Domain extends IPropertyType
{
    String EXPERIMENTAL_FUZZ_STORAGE_NAME = "DomainImpl.fuzzStorageName";

    Object get_Ts();
    Container getContainer();
    @Nullable DomainKind<?> getDomainKind();
    String getName();
    String getTitle();
    String getDescription();
    int getTypeId();
    @Override
    String getTypeURI();

    Set<Container> getInstanceContainers();
    Set<Container> getInstanceContainers(User user, Class<? extends Permission> perm);

    void setName(String name);
    void setTitle(String title);
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

    @Deprecated // Use addProperty(PropertyStorageSpec)
    DomainProperty addProperty(PropertyStorageSpec spec, @Nullable String propSuffix);

    List<BaseColumnInfo> getColumns(TableInfo sourceTable, ColumnInfo lsidColumn, Container container, User user);

    boolean isMutable();

    /*
     * This returns a lock which will acquire an UPDATE lock on the domain row in the database.
     * This can be called at the beginning of a transaction to help reduce the chance of a deadlock.
     * This pattern effectively forces all callers who are trying to manipulate this domain to queue up.
     */
    Lock getDatabaseLock();
    void lockForUpdateDelete(DbSchema lockSchema);

    void delete(@Nullable User user) throws DomainNotFoundException;
    default void delete(@Nullable User user, @Nullable String auditUserComment) throws DomainNotFoundException
    {
        delete(user);
    }
    void save(User user) throws ChangePropertyDescriptorException;
    void save(User user, @Nullable Map<String, Object> newRecordMap, @Nullable List<? extends GWTPropertyDescriptor> calculatedFields) throws ChangePropertyDescriptorException;
    void save(User user, @Nullable String auditComment, @Nullable String auditUserComment,
              @Nullable Map<String, Object> oldRecordMap, @Nullable Map<String, Object> newRecordMap,
              @Nullable List<? extends GWTPropertyDescriptor> oldCalculatedFields, @Nullable List<? extends GWTPropertyDescriptor> newCalculatedFields) throws ChangePropertyDescriptorException;

    /** Returns true if this domain has not yet been saved. */
    boolean isNew();

    /**
     * This returns a map of names -> PropertyDescriptor that is useful for import that includes all the
     * different names that a column may be referred to, dealing with naming collisions between aliases and property names
     * in the right way.
     * @param includeMVIndicators whether to include the missing value indicator "column" names in the map
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
     * are in addition to those from the {@link DomainKind#getPropertyIndices(Domain)}.
     * Currently, the indices are not saved as a part of the domain definition.
     */
    void setPropertyIndices(@NotNull Set<PropertyStorageSpec.Index> indices);
    void setPropertyIndices(@NotNull List<GWTIndex> indices, @Nullable Set<String> lowerReservedNames);
    @NotNull Set<PropertyStorageSpec.Index> getPropertyIndices();

    /**
     * @param shouldDeleteAllData Flag that all data should be deleted, initial use case is for Lists and Datasets
     *                            having all their user-editable fields replaced via Import Fields form
     */
    void setShouldDeleteAllData(boolean shouldDeleteAllData);
    boolean isShouldDeleteAllData();
    boolean isProvisioned();

    List<String> getDisabledSystemFields();
    void setDisabledSystemFields(@Nullable List<String> disabledSystemFields);

    @Nullable
    TemplateInfo getTemplateInfo();
}
