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

package org.labkey.experiment.api.property;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.MVDisplayColumnFactory;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ServerPrimaryKeyLock;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainAuditProvider;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JdbcUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class DomainImpl implements Domain
{
    boolean _new;
    boolean _enforceStorageProperties = true;
    DomainDescriptor _ddOld;
    DomainDescriptor _dd;
    List<DomainPropertyImpl> _properties;
    private Set<PropertyStorageSpec.ForeignKey> _propertyForeignKeys = Collections.emptySet();
    private Set<PropertyStorageSpec.Index> _propertyIndices = Collections.emptySet();
    private boolean _shouldDeleteAllData = false;

    // NOTE we could put responsibilty for generating column names on the StorageProvisioner
    // But then we'd have the situation of StorageProvisioner knowing about/updating Domains, which seems fraught
    transient AliasManager _aliasManager = null;


    public DomainImpl(DomainDescriptor dd)
    {
        _dd = dd;
        List<PropertyDescriptor> pds = OntologyManager.getPropertiesForType(getTypeURI(), getContainer());
        _properties = new ArrayList<>(pds.size());
        List<DomainPropertyManager.ConditionalFormatWithPropertyId> allFormats = DomainPropertyManager.get().getConditionalFormats(getContainer());
        for (PropertyDescriptor pd : pds)
        {
            List<ConditionalFormat> formats = new ArrayList<>();
            for (DomainPropertyManager.ConditionalFormatWithPropertyId format : allFormats)
            {
                if (format.getPropertyId() == pd.getPropertyId())
                {
                    formats.add(format);
                }
            }
            DomainPropertyImpl property = new DomainPropertyImpl(this, pd, formats);
            _properties.add(property);
        }
    }

    public DomainImpl(Container container, String uri, String name)
    {
        _new = true;
        _dd = new DomainDescriptor.Builder(uri, container)
                .setName(name)
                .build();
        _properties = new ArrayList<>();
    }

    public Object get_Ts()
    {
        return _dd.get_Ts();
    }

    public Container getContainer()
    {
        return _dd.getContainer();
    }

    private DomainKind _kind = null;

    public synchronized DomainKind getDomainKind()
    {
        if (null == _kind)
            _kind = PropertyService.get().getDomainKind(getTypeURI());
        return _kind;
    }

    public String getName()
    {
        return _dd.getName();
    }

    public String getLabel()
    {
        DomainKind kind = getDomainKind();
        if (kind == null)
        {
            return "Domain '" + getName() + "'";
        }
        else
        {
            return getDomainKind().getTypeLabel(this);
        }
    }

    public String getLabel(Container container)
    {
        String ret = getLabel();
        if (!getContainer().equals(container))
        {
            ret += "(" + getContainer().getPath() + ")";
        }
        return ret;
    }


    @Nullable   // null if not provisioned
    public String getStorageTableName()
    {
        return _dd.getStorageTableName();
    }

    @Override
    public void setEnforceStorageProperties(boolean enforceStorageProperties)
    {
        _enforceStorageProperties = enforceStorageProperties;
    }

    /**
     * @return all containers that contain at least one row of this domain's data.
     * Only works for domains that are persisted in exp.object, not those with their own provisioned hard tables
     */
    public Set<Container> getInstanceContainers()
    {
        assert getStorageTableName() == null : "This method only works on domains persisted in exp.object";
        SQLFragment sqlObjectIds = getDomainKind().sqlObjectIdsInDomain(this);
        if (sqlObjectIds == null)
            return Collections.emptySet();
        SQLFragment sql = new SQLFragment("SELECT DISTINCT exp.object.container FROM exp.object WHERE exp.object.objectid IN ");
        sql.append(sqlObjectIds);
        Set<Container> ret = new HashSet<>();
        for (String id : new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(String.class))
        {
            ret.add(ContainerManager.getForId(id));
        }
        return ret;
    }

    /**
     * @return all containers that contain at least one row of this domain's data, and where the user has the specified permission
     * Only works for domains that are persisted in exp.object, not those with their own provisioned hard tables
     */
    public Set<Container> getInstanceContainers(User user, Class<? extends Permission> perm)
    {
        Set<Container> ret = new HashSet<>();
        for (Container c : getInstanceContainers())
        {
            if (c.hasPermission(user, perm))
            {
                ret.add(c);
            }
        }
        return ret;
    }

    public String getDescription()
    {
        return _dd.getDescription();
    }

    public int getTypeId()
    {
        return _dd.getDomainId();
    }

    public void setDescription(String description)
    {
        _dd = _dd.edit().setDescription(description).build();
    }

    @NotNull
    public List<? extends DomainPropertyImpl> getProperties()
    {
        return Collections.unmodifiableList(_properties);
    }

    public List<DomainProperty> getNonBaseProperties()
    {
        Set<String> basePropertyNames = new HashSet<>();
        for (PropertyStorageSpec spec : getDomainKind().getBaseProperties())
            basePropertyNames.add(spec.getName().toLowerCase());

        List<DomainProperty> nonBaseProperties = new ArrayList<>();
        for (DomainPropertyImpl prop : getProperties())
        {
            if (!basePropertyNames.contains(prop.getName().toLowerCase()))
                nonBaseProperties.add(prop);
        }
        return nonBaseProperties;
    }

    public Set<DomainProperty> getBaseProperties()
    {
        Set<String> basePropertyNames = new HashSet<>();
        for (PropertyStorageSpec spec : getDomainKind().getBaseProperties())
            basePropertyNames.add(spec.getName().toLowerCase());

        Set<DomainProperty> baseProperties = new HashSet<>();
        for (DomainPropertyImpl prop : getProperties())
        {
            if (basePropertyNames.contains(prop.getName().toLowerCase()))
                baseProperties.add(prop);
        }
        return baseProperties;
    }

    public void setPropertyIndex(DomainProperty prop, int index)
    {
        if (index < 0 || index >= _properties.size())
        {
            throw new IndexOutOfBoundsException();
        }
        //noinspection SuspiciousMethodCalls
        if (!_properties.remove(prop))
        {
            throw new IllegalArgumentException("The property is not part of this domain");
        }
        _properties.add(index, (DomainPropertyImpl) prop);
    }

    public ActionURL urlShowData(ContainerUser context)
    {
        return getDomainKind().urlShowData(this, context);
    }

    public void delete(@Nullable User user) throws DomainNotFoundException
    {
        ExperimentService.Interface exp = ExperimentService.get();
        Lock domainLock =  getLock(_dd);
        try (DbScope.Transaction transaction = exp.getSchema().getScope().ensureTransaction(domainLock))
        {
            DefaultValueService.get().clearDefaultValues(getContainer(), this);
            OntologyManager.deleteDomain(getTypeURI(), getContainer());
            StorageProvisioner.drop(this);
            addAuditEvent(user, String.format("The domain %s was deleted", _dd.getName()));
            transaction.commit();
        }
    }

    private boolean isNew()
    {
        return _new;
    }

    public void save(User user) throws ChangePropertyDescriptorException
    {
        save(user, false);
    }


    static Lock getLock(DomainDescriptor dd)
    {
        // TODO lock by dd.getDomainURI instead, need alternate ServerPrimaryKeyLock constructor
        if (dd.getDomainId() == 0)
        {
            return new DbScope.ServerNoopLock();
        }
        else
        {
            DbSchema s = ExperimentService.get().getSchema();
            return new ServerPrimaryKeyLock(false, s.getTable("domaindescriptor"), dd.getDomainId());
        }
    }


    public void save(User user, boolean allowAddBaseProperty) throws ChangePropertyDescriptorException
    {
        ExperimentService.Interface exp = ExperimentService.get();

        // NOTE: the synchronization here does not remove the need to add better synchronization in StorageProvisioner, but it helps
        Lock domainLock = getLock(_dd);

        try (DbScope.Transaction transaction = exp.getSchema().getScope().ensureTransaction(domainLock))
        {
            List<DomainProperty> checkRequiredStatus = new ArrayList<>();
            if (isNew())
            {
                // consider: optimistic concurrency check here?
                Table.insert(user, OntologyManager.getTinfoDomainDescriptor(), _dd);
                _dd = OntologyManager.getDomainDescriptor(_dd.getDomainURI(), _dd.getContainer());
                // CONSIDER put back if we want automatic provisioning for serveral DomainKinds
                // StorageProvisioner.create(this);
                addAuditEvent(user, String.format("The domain %s was created", _dd.getName()));
            }
            else
            {
                DomainDescriptor ddCheck = OntologyManager.getDomainDescriptor(_dd.getDomainId());
                if (!JdbcUtil.rowVersionEqual(ddCheck.get_Ts(), _dd.get_Ts()))
                    throw new Table.OptimisticConflictException("Domain has been updated by another user or process.", Table.SQLSTATE_TRANSACTION_STATE, 0);

                // call OntololgyManager.updateDomainDescriptor() to invalidate proper caches
                _dd = OntologyManager.updateDomainDescriptor(_dd);

                // we expect _ddOld should be null if we only have property changes
                if (null != _ddOld)
                    addAuditEvent(user, String.format("The descriptor of domain %s was updated", _dd.getName()));
            }
            boolean propChanged = false;
            int sortOrder = 0;

            List<DomainProperty> propsDropped = new ArrayList<>();
            List<DomainProperty> propsAdded = new ArrayList<>();

            DomainKind kind = getDomainKind();
            boolean hasProvisioner = null != kind && null != kind.getStorageSchemaName();

            // Certain provisioned table types (Lists and Datasets) get wiped when their fields are replaced via field Import
            if (hasProvisioner && isShouldDeleteAllData())
            {
                try
                {
                    kind.getTableInfo(user, getContainer(), getName()).getUpdateService().truncateRows(user, getContainer(), null, null);
                }
                catch (QueryUpdateServiceException | BatchValidationException | SQLException e)
                {
                   throw new ChangePropertyDescriptorException(e);
                }
            }

            // Delete first #8978
            for (DomainPropertyImpl impl : _properties)
            {
                if (impl._deleted || (impl.isRecreateRequired()))
                {
                    impl.delete(user);
                    propsDropped.add(impl);
                    propChanged = true;
                }
            }

            if (hasProvisioner && _enforceStorageProperties)
            {
                if (!propsDropped.isEmpty())
                {
                    StorageProvisioner.dropProperties(this, propsDropped);
                }
            }

            // Keep track of the intended final name for each updated property, and its sort order
            Map<DomainPropertyImpl, Pair<String, Integer>> finalNames = new HashMap<>();

            // Now add and update #8978
            for (DomainPropertyImpl impl : _properties)
            {
                if (!impl._deleted)
                {
                    // make sure all properties have storageColumnName
                    if (null == impl._pd.getStorageColumnName())
                        generateStorageColumnName(impl._pd);

                    if (impl.isRecreateRequired())
                    {
                        impl.markAsNew();
                    }

                    if (impl.isNew())
                    {
                        if (impl._pd.isRequired())
                            checkRequiredStatus.add(impl);
                        propsAdded.add(impl);
                        propChanged = true;
                    }
                    else
                    {
                        propChanged |= impl.isDirty();
                        if (impl._pdOld != null)
                        {
                            // If this field is newly required, or it's required and we're disabling MV indicators on
                            // it, make sure that all of the rows have values for it
                            if ((!impl._pdOld.isRequired() && impl._pd.isRequired()) ||
                                    (impl._pd.isRequired() && !impl._pd.isMvEnabled() && impl._pdOld.isMvEnabled()))
                            {
                                checkRequiredStatus.add(impl);
                            }

                            //if size constraints have decreased check
                            if(impl._pdOld.getScale() > impl._pd.getScale())
                                checkAndThrowSizeConstraints(kind, this, impl);
                        }

                        if (impl.isDirty())
                        {
                            if (null != impl._pdOld && !impl._pdOld.getName().equalsIgnoreCase(impl._pd.getName()))
                            {
                                finalNames.put(impl, new Pair<>(impl.getName(), sortOrder));
                                // Save any fields whose name changed with a temp, guaranteed unique name. This is important in case a single save
                                // is renaming "Field1"->"Field2" and "Field2"->"Field1". See issue 17020
                                String tmpName = "~tmp" + new GUID().toStringNoDashes();
                                impl.setName(tmpName);
                                impl._pd.setStorageColumnName(tmpName);
                            }
                        }
                    }
                    impl.save(user, _dd, sortOrder++);      // Always save to preserve order
                }
            }

            // Then rename them all to their final name
            for (Map.Entry<DomainPropertyImpl, Pair<String, Integer>> entry : finalNames.entrySet())
            {
                DomainPropertyImpl domainProperty = entry.getKey();
                String name = entry.getValue().getKey();
                int order = entry.getValue().getValue().intValue();
                domainProperty.setName(name);
                generateStorageColumnName(domainProperty._pd);
                domainProperty.save(user, _dd, order);
            }

            _new = false;

            // Do the call to add the new properties last, after deletes and renames of existing properties
            if (propChanged && hasProvisioner && _enforceStorageProperties)
            {
                if (!propsAdded.isEmpty())
                {
                    StorageProvisioner.addProperties(this, propsAdded, allowAddBaseProperty);
                }

                addAuditEvent(user, String.format("The column(s) of domain %s were modified", _dd.getName()));
            }

            if (!checkRequiredStatus.isEmpty() && null != kind)
            {
                for (DomainProperty prop : checkRequiredStatus)
                {
                    boolean hasRows = kind.hasNullValues(this, prop);
                    if (hasRows)
                    {
                        throw new IllegalStateException("The property \"" + prop.getName() + "\" cannot be required when it contains rows with blank values.");
                    }
                }
            }

            // Invalidate even if !propChanged, because ordering might have changed (#25296)
            if (getDomainKind() != null)
                getDomainKind().invalidate(this);

            transaction.commit();
        }
    }

    private void checkAndThrowSizeConstraints(DomainKind kind, Domain domain, DomainProperty prop )
    {
        boolean tooLong = kind.exceedsMaxLength(this, prop);
        if (tooLong)
        {
            throw new IllegalStateException("The property \"" + prop.getName() + "\" cannot be scaled down. It contains existing values exceeding ["+ prop.getScale() + "] characters.");
        }
    }

    private void addAuditEvent(@Nullable User user, String comment)
    {
        if (user != null)
        {
            DomainAuditProvider.DomainAuditEvent event = new DomainAuditProvider.DomainAuditEvent(getContainer().getId(), comment);

            if (_dd.getProject() != null)
                event.setProjectId(_dd.getProject().getId());

            event.setDomainUri(getTypeURI());
            event.setDomainName(getName());

            AuditLogService.get().addEvent(user, event);
        }
    }

    public Map<String, DomainProperty> createImportMap(boolean includeMVIndicators)
    {
        List<DomainProperty> properties = new ArrayList<DomainProperty>(_properties);
        return ImportAliasable.Helper.createImportMap(properties, includeMVIndicators);
    }

    public DomainProperty addPropertyOfPropertyDescriptor(PropertyDescriptor pd)
    {
        assert pd.getPropertyId() == 0;
        assert pd.getContainer().equals(getContainer());

        // Warning: Shallow copy
        DomainPropertyImpl ret = new DomainPropertyImpl(this, pd.clone());
        _properties.add(ret);
        return ret;
    }

    public DomainProperty addProperty()
    {
        PropertyDescriptor pd = new PropertyDescriptor();
        pd.setContainer(getContainer());
        pd.setRangeURI(PropertyType.STRING.getTypeUri());
        pd.setScale(PropertyType.STRING.getScale());
        DomainPropertyImpl ret = new DomainPropertyImpl(this, pd);
        _properties.add(ret);
        return ret;
    }

    public DomainProperty addProperty(PropertyStorageSpec spec)
    {
        PropertyDescriptor pd = new PropertyDescriptor();
        pd.setContainer(getContainer());
        pd.setName(spec.getName());
        pd.setJdbcType(spec.getJdbcType(), spec.getSize());
        pd.setNullable(spec.isNullable());
//        pd.setAutoIncrement(spec.isAutoIncrement());      // always false in PropertyDescriptor
        pd.setMvEnabled(spec.isMvEnabled());
        pd.setPropertyURI(getTypeURI() + ":field-" + spec.getName());
        pd.setDescription(spec.getDescription());
        pd.setImportAliases(spec.getImportAliases());
        pd.setScale(spec.getSize());
        DomainPropertyImpl ret = new DomainPropertyImpl(this, pd);
        _properties.add(ret);
        return ret;
    }

    public String getTypeURI()
    {
        return _dd.getDomainURI();
    }

    public DomainPropertyImpl getProperty(int id)
    {
        for (DomainPropertyImpl prop : getProperties())
        {
            if (prop.getPropertyId() == id)
                return prop;
        }
        return null;
    }

    public DomainPropertyImpl getPropertyByURI(String uri)
    {
        for (DomainPropertyImpl prop : getProperties())
        {
            if (prop.getPropertyURI().equalsIgnoreCase(uri))
            {
                return prop;
            }
        }
        return null;
    }

    public DomainPropertyImpl getPropertyByName(String name)
    {
        for (DomainPropertyImpl prop : getProperties())
        {
            if (prop.getName().equalsIgnoreCase(name))
                return prop;
        }
        return null;
    }

    public List<ColumnInfo> getColumns(TableInfo sourceTable, ColumnInfo lsidColumn, Container container, User user)
    {
        List<ColumnInfo> result = new ArrayList<>();
        for (DomainProperty property : getProperties())
        {
            ColumnInfo column = new PropertyColumn(property.getPropertyDescriptor(), lsidColumn, container, user, false);
            result.add(column);
            if (property.isMvEnabled())
            {
                column.setMvColumnName(new FieldKey(null, column.getName() + MvColumn.MV_INDICATOR_SUFFIX));
                result.addAll(MVDisplayColumnFactory.createMvColumns(column, property.getPropertyDescriptor(), lsidColumn));
            }
        }
        return result;
    }

    @Override
    public int hashCode()
    {
        return _dd.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof DomainImpl))
            return false;
        // once a domain has been edited, it no longer equals any other domain:
        if (_ddOld != null || ((DomainImpl) obj)._ddOld != null)
            return false;
        return (_dd.equals(((DomainImpl) obj)._dd));
    }

    @Override
    public String toString()
    {
        return getTypeURI();
    }

    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys()
    {
        return _propertyForeignKeys;
    }

    public void setPropertyForeignKeys(Set<PropertyStorageSpec.ForeignKey> propertyForeignKeys)
    {
        _propertyForeignKeys = propertyForeignKeys;
    }

    @Override
    @NotNull
    public Set<PropertyStorageSpec.Index> getPropertyIndices()
    {
        return _propertyIndices;
    }

    public void setPropertyIndices(@NotNull Set<PropertyStorageSpec.Index> propertyIndices)
    {
        _propertyIndices = propertyIndices;
    }

    @Override
    public void setShouldDeleteAllData(boolean shouldDeleteAllData)
    {
        _shouldDeleteAllData = shouldDeleteAllData;
    }

    @Override
    public boolean isShouldDeleteAllData()
    {
        // Only certain domain kinds, Lists & Datasets, will return true
        if (getDomainKind().isDeleteAllDataOnFieldImport() && _shouldDeleteAllData)
        {
            return true;
        }
        else return false;
    }


    public void generateStorageColumnName(PropertyDescriptor pd)
    {
        if (null == _aliasManager)
        {
            _aliasManager = new AliasManager(DbSchema.get("exp"));
            DomainKind k = getDomainKind();
            if (null != k)
            {
                for (PropertyStorageSpec s : k.getBaseProperties())
                {
                    _aliasManager.claimAlias(s.getName(),s.getName());
                }
            }
            for (DomainPropertyImpl dp : this.getProperties())
            {
                if (null != dp._pd && !dp._deleted && null != dp._pd.getStorageColumnName())    // Don't claim deleted names (#23295)
                    _aliasManager.claimAlias(dp._pd.getStorageColumnName(), dp.getName());
            }
        }

        // we could just call _aliasManager.decideAlias(pd.getName()) here, however the StorageProvisioner
        // indexes, and foreignkeys still reply on storage names matching prooperty names.  So try to keep the
        // names the same if at all possible (especially short names)
        String storage = null;
        if (pd.getName().length() < 60)
            storage = _aliasManager.decideAlias(pd.getName(), pd.getName());
        else
            storage = _aliasManager.decideAlias(pd.getName());
        pd.setStorageColumnName(storage);
    }

    public boolean isProvisioned()
    {
        return getStorageTableName() != null && getDomainKind().getStorageSchemaName() != null;
    }
}
