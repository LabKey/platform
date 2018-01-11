/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.collections.Sets;
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
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainAuditProvider;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainPropertyAuditProvider;
import org.labkey.api.exp.property.DomainTemplate;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // NOTE we could put responsibility for generating column names on the StorageProvisioner
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
        this(container, uri, name, null);
    }

    public DomainImpl(Container container, String uri, String name, @Nullable TemplateInfo templateInfo)
    {
        _new = true;
        _dd = new DomainDescriptor.Builder(uri, container)
                .setName(name)
                .setTemplateInfoObject(templateInfo)
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
        for (PropertyStorageSpec spec : getDomainKind().getBaseProperties(this))
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
        for (PropertyStorageSpec spec : getDomainKind().getBaseProperties(this))
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
        ExperimentService exp = ExperimentService.get();
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
        ExperimentService exp = ExperimentService.get();

        // NOTE: the synchronization here does not remove the need to add better synchronization in StorageProvisioner, but it helps
        Lock domainLock = getLock(_dd);

        try (DbScope.Transaction transaction = exp.getSchema().getScope().ensureTransaction(domainLock))
        {
            List<DomainProperty> checkRequiredStatus = new ArrayList<>();
            boolean isDomainNew = false;         // #32406 Need to capture because _new changes during the process
            if (isNew())
            {
                // consider: optimistic concurrency check here?
                Table.insert(user, OntologyManager.getTinfoDomainDescriptor(), _dd);
                _dd = OntologyManager.getDomainDescriptor(_dd.getDomainURI(), _dd.getContainer());
                // CONSIDER put back if we want automatic provisioning for several DomainKinds
                // StorageProvisioner.create(this);
                isDomainNew = true;
            }
            else
            {
                DomainDescriptor ddCheck = OntologyManager.getDomainDescriptor(_dd.getDomainId());
                if (!JdbcUtil.rowVersionEqual(ddCheck.get_Ts(), _dd.get_Ts()))
                    throw new OptimisticConflictException("Domain has been updated by another user or process.", Table.SQLSTATE_TRANSACTION_STATE, 0);

                // call OntologyManager.updateDomainDescriptor() to invalidate proper caches
                _dd = OntologyManager.updateDomainDescriptor(_dd);
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

            Set<String> baseProperties = Sets.newCaseInsensitiveHashSet();
            if (null != kind)
            {
                for (PropertyStorageSpec s : kind.getBaseProperties(this))
                    baseProperties.add(s.getName());
            }

            // Compile audit info for every property change
            List<PropertyChangeAuditInfo> propertyAuditInfo = new ArrayList<>();

            // Delete first #8978
            for (DomainPropertyImpl impl : _properties)
            {
                if (impl._deleted || (impl.isRecreateRequired()))
                {
                    impl.delete(user);
                    propsDropped.add(impl);
                    propChanged = true;
                    propertyAuditInfo.add(new PropertyChangeAuditInfo(impl, false));
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
            Map<DomainProperty, Object> defaultValueMap = new HashMap<>();

            // Now add and update #8978
            for (DomainPropertyImpl impl : _properties)
            {
                if (!impl._deleted)
                {
                    // make sure all properties have storageColumnName
                    if (null == impl._pd.getStorageColumnName())
                    {
                        if (!allowAddBaseProperty && baseProperties.contains(impl._pd.getName()))
                            impl._pd.setStorageColumnName(impl._pd.getName()); // Issue 29047: if we allow base property (like "date"), we're later going to use the base property name for storage
                        else
                            generateStorageColumnName(impl._pd);
                    }

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

                            // check if string size constraints have decreased
                            if (impl._pdOld.isStringType() && isSmallerSize(impl._pdOld.getScale(), impl._pd.getScale()))
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

                    // Auditing:gather validators and conditional formats before save; then build diff using new validators and formats after save
                    boolean isImplNew = impl.isNew();
                    PropertyDescriptor pdOld = impl._pdOld;
                    String oldValidators = null != pdOld ? PropertyChangeAuditInfo.renderValidators(pdOld) : null;
                    String oldFormats = null != pdOld ? PropertyChangeAuditInfo.renderConditionalFormats(pdOld) : null;
                    impl.save(user, _dd, sortOrder++);  // Automatically preserve order

                    String defaultValue = impl.getDefaultValue();
                    Object converted = null != defaultValue ? ConvertUtils.convert(defaultValue, impl.getPropertyDescriptor().getJavaClass()) : null;
                    defaultValueMap.put(impl, converted);

                    if (isImplNew)
                        propertyAuditInfo.add(new PropertyChangeAuditInfo(impl, true));
                    else if (null != pdOld)
                        propertyAuditInfo.add(new PropertyChangeAuditInfo(impl, pdOld, oldValidators, oldFormats));
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

            try
            {
                DefaultValueService.get().setDefaultValues(getContainer(), defaultValueMap);
            }
            catch (ExperimentException e)
            {
                throw new RuntimeException(e);
            }

            _new = false;

            // Do the call to add the new properties last, after deletes and renames of existing properties
            if (propChanged && hasProvisioner && _enforceStorageProperties)
            {
                if (!propsAdded.isEmpty())
                {
                    StorageProvisioner.addProperties(this, propsAdded, allowAddBaseProperty);
                }
            }

            if (!checkRequiredStatus.isEmpty() && null != kind)
            {
                for (DomainProperty prop : checkRequiredStatus)
                {
                    boolean hasRows = kind.hasNullValues(this, prop);
                    if (hasRows)
                    {
                        throw new ChangePropertyDescriptorException("The property \"" + prop.getName() + "\" cannot be required when it contains rows with blank values.");
                    }
                }
            }

            // Invalidate even if !propChanged, because ordering might have changed (#25296)
            if (getDomainKind() != null)
                getDomainKind().invalidate(this);

            if (isDomainNew)
                addAuditEvent(user, String.format("The domain %s was created", _dd.getName()));

            if (propChanged)
            {
                final Integer domainEventId = addAuditEvent(user, String.format("The column(s) of domain %s were modified", _dd.getName()));
                propertyAuditInfo.forEach(auditInfo -> {
                    addPropertyAuditEvent(user, auditInfo.getProp(), auditInfo.getAction(), domainEventId, getName(), auditInfo.getDetails());
                });
            }
            else if (!isDomainNew)
            {
                addAuditEvent(user, String.format("The descriptor of domain %s was updated", _dd.getName()));
            }
            transaction.commit();
        }
    }

    // Return true if newSize is smaller than oldSize taking into account -1 is max size
    private boolean isSmallerSize(int oldSize, int newSize)
    {
        if (newSize == oldSize)
            return false;
        if (newSize == -1)
            return false;
        if (oldSize == -1)
            return true;
        return oldSize > newSize;
    }

    private void checkAndThrowSizeConstraints(DomainKind kind, Domain domain, DomainProperty prop)
    {
        boolean tooLong = kind.exceedsMaxLength(this, prop);
        if (tooLong)
        {
            throw new IllegalStateException("The property \"" + prop.getName() + "\" cannot be scaled down. It contains existing values exceeding ["+ prop.getScale() + "] characters.");
        }
    }

    private Integer addAuditEvent(@Nullable User user, String comment)
    {
        if (user != null)
        {
            DomainAuditProvider.DomainAuditEvent event = new DomainAuditProvider.DomainAuditEvent(getContainer().getId(), comment);

            if (_dd.getProject() != null)
                event.setProjectId(_dd.getProject().getId());

            event.setDomainUri(getTypeURI());
            event.setDomainName(getName());

            AuditTypeEvent retEvent = AuditLogService.get().addEvent(user, event);
            return null != retEvent ? retEvent.getRowId() : null;
        }
        return null;
    }

    private void addPropertyAuditEvent(@Nullable User user, DomainProperty prop, String action, Integer domainEventId, String domainName, String comment)
    {
        DomainPropertyAuditProvider.DomainPropertyAuditEvent event =
                new DomainPropertyAuditProvider.DomainPropertyAuditEvent(getContainer().getId(), prop.getPropertyURI(), prop.getName(),
                                                                         action, domainEventId, domainName, comment);
        AuditLogService.get().addEvent(user, event);
    }

    private static class PropertyChangeAuditInfo
    {
        private final DomainProperty _prop;
        private final String _action;
        private final String _details;    // to go in comments

        public PropertyChangeAuditInfo(DomainPropertyImpl prop, boolean isCreated)
        {
            _prop = prop;
            _action = isCreated ? "Created" : "Deleted";
            _details = isCreated ? makeNewPropAuditComment(prop) : "";
        }

        public PropertyChangeAuditInfo(DomainPropertyImpl prop, PropertyDescriptor pdOld,
                                       String oldValidators, String oldFormats)
        {
            _prop = prop;
            _action = "Modified";
            _details = makeModifiedPropAuditComment(prop, pdOld, oldValidators, oldFormats);
        }

        public DomainProperty getProp()
        {
            return _prop;
        }

        public String getAction()
        {
            return _action;
        }

        public String getDetails()
        {
            return _details;
        }

        private String makeNewPropAuditComment(DomainProperty prop)
        {
            StringBuilder str = new StringBuilder();
            str.append("Name: ").append(prop.getName()).append("; ");
            str.append("Label: ").append(renderCheckingBlank(prop.getLabel())).append("; ");
            str.append("Type: ").append(prop.getPropertyType().getXarName()).append("; ");
            if (prop.getPropertyType().getJdbcType().isText())
                str.append("Scale: ").append(prop.getScale()).append("; ");

            Lookup lookup = prop.getLookup();
            if (null != lookup)
            {
                str.append("Lookup: [");
                if (null != lookup.getContainer())
                    str.append("Container: ").append(lookup.getContainer().getName()).append(", ");
                str.append("Schema: ").append(lookup.getSchemaName()).append(", ")
                   .append("Query: ").append(lookup.getQueryName()).append("]; ");
            }

            str.append("Description: ").append(renderCheckingBlank(prop.getDescription())).append("; ");
            str.append("Format: ").append(renderCheckingBlank(prop.getFormat())).append("; ");
            str.append("URL: ").append(renderCheckingBlank(prop.getURL())).append("; ");
            str.append("PHI: ").append(prop.getPHI().toString()).append("; ");
            str.append("ImportAliases: ").append(renderImportAliases(prop.getPropertyDescriptor())).append("; ");
            str.append("Validators: ").append(renderValidators(prop.getPropertyDescriptor())).append("; ");
            str.append("ConditionalFormats: ").append(renderConditionalFormats(prop.getPropertyDescriptor())).append("; ");
            str.append("DefaultValueType: ").append(renderDefaultValueType(prop.getPropertyDescriptor())).append("; ");
            str.append("DefaultScale: ").append(prop.getDefaultScale().getLabel()).append("; ");
            str.append("Required: ").append(renderBool(prop.isRequired())).append("; ");
            str.append("Hidden: ").append(renderBool(prop.isHidden())).append("; ");
            str.append("MvEnabled: ").append(renderBool(prop.isMvEnabled())).append("; ");
            str.append("Measure: ").append(renderBool(prop.isMeasure())).append("; ");
            str.append("Dimension: ").append(renderBool(prop.isDimension())).append("; ");
            str.append("ShownInInsert: ").append(renderBool(prop.isShownInInsertView())).append("; ");
            str.append("ShownInDetails: ").append(renderBool(prop.isShownInDetailsView())).append("; ");
            str.append("ShownInUpdate: ").append(renderBool(prop.isShownInUpdateView())).append("; ");
            str.append("RecommendedVariable: ").append(renderBool(prop.isRecommendedVariable())).append("; ");
            str.append("ExcludedFromShifting: ").append(renderBool(prop.isExcludeFromShifting())).append("; ");
            return str.toString();
        }

        private String makeModifiedPropAuditComment(DomainPropertyImpl prop, PropertyDescriptor pdOld, String oldValidators, String oldFormats)
        {
            StringBuilder str = new StringBuilder();
            if (!pdOld.getName().equals(prop.getName()))
                str.append("Name: ").append(renderOldVsNew(pdOld.getName(), prop.getName())).append("; ");
            if (!StringUtils.equals(pdOld.getLabel(), prop.getLabel()))
                str.append("Label: ").append(renderOldVsNew(renderCheckingBlank(pdOld.getLabel()), renderCheckingBlank(prop.getLabel()))).append("; ");
            if (null != pdOld.getPropertyType() && !pdOld.getPropertyType().equals(prop.getPropertyType()))
                str.append("Type: ").append(renderOldVsNew(pdOld.getPropertyType().getXarName(), prop.getPropertyType().getXarName())).append("; ");
            if (prop.getPropertyType().getJdbcType().isText())
                if (pdOld.getScale() != prop.getScale())
                    str.append("Scale: ").append(renderOldVsNew(Integer.toString(pdOld.getScale()), Integer.toString(prop.getScale()))).append("; ");

            if (!StringUtils.equals(pdOld.getLookupSchema(), prop.getPropertyDescriptor().getLookupSchema()) ||
                !StringUtils.equals(pdOld.getLookupQuery(), prop.getPropertyDescriptor().getLookupQuery()) ||
                !StringUtils.equals(pdOld.getLookupContainer(), prop.getPropertyDescriptor().getLookupContainer()))
            {
                renderLookupDiff(prop.getPropertyDescriptor(), pdOld, str);
            }

            if (!StringUtils.equals(pdOld.getDescription(), prop.getDescription()))
                str.append("Description: ").append(renderOldVsNew(renderCheckingBlank(pdOld.getDescription()), renderCheckingBlank(prop.getDescription()))).append("; ");
            if (!StringUtils.equals(prop.getFormat(), prop.getFormat()))
                str.append("Format: ").append(renderOldVsNew(renderCheckingBlank(pdOld.getFormat()), renderCheckingBlank(prop.getFormat()))).append("; ");
            if (!StringUtils.equals((null != pdOld.getURL() ? pdOld.getURL().toString() : null), prop.getURL()))
                str.append("URL: ").append(renderOldVsNew(renderCheckingBlank(null != pdOld.getURL() ? pdOld.getURL().toString() : null), renderCheckingBlank(prop.getURL()))).append("; ");
            if (!pdOld.getPHI().equals(prop.getPHI()))
                str.append("PHI: ").append(renderOldVsNew(pdOld.getPHI().getLabel(), prop.getPHI().getLabel())).append("; ");

            renderImportAliasesDiff(prop, pdOld, str);
            renderValidatorsDiff(prop, oldValidators, str);
            renderConditionalFormatsDiff(prop, oldFormats, str);
            renderDefaultValueTypeDiff(prop, pdOld, str);

            if (!pdOld.getDefaultScale().getLabel().equals(prop.getDefaultScale().getLabel()))
                str.append("DefaultScale: ").append(renderOldVsNew(pdOld.getDefaultScale().getLabel(), prop.getDefaultScale().getLabel())).append("; ");
            if (pdOld.isRequired() != prop.isRequired())
                str.append("Required: ").append(renderOldVsNew(renderBool(pdOld.isRequired()), renderBool(prop.isRequired()))).append("; ");
            if (pdOld.isHidden() != prop.isHidden())
                str.append("Hidden: ").append(renderOldVsNew(renderBool(pdOld.isHidden()), renderBool(prop.isHidden()))).append("; ");
            if (pdOld.isMvEnabled() != prop.isMvEnabled())
                str.append("MvEnabled: ").append(renderOldVsNew(renderBool(pdOld.isMvEnabled()), renderBool(prop.isMvEnabled()))).append("; ");
            if (pdOld.isMeasure() != prop.isMeasure())
                str.append("Measure: ").append(renderOldVsNew(renderBool(pdOld.isMeasure()), renderBool(prop.isMeasure()))).append("; ");
            if (pdOld.isDimension() != prop.isDimension())
                str.append("Dimension: ").append(renderOldVsNew(renderBool(pdOld.isDimension()), renderBool(prop.isDimension()))).append("; ");
            if (pdOld.isShownInInsertView() != prop.isShownInInsertView())
                str.append("ShownInInsert: ").append(renderOldVsNew(renderBool(pdOld.isShownInInsertView()), renderBool(prop.isShownInInsertView()))).append("; ");
            if (pdOld.isShownInDetailsView() != prop.isShownInDetailsView())
                str.append("ShownInDetails: ").append(renderOldVsNew(renderBool(pdOld.isShownInDetailsView()), renderBool(prop.isShownInDetailsView()))).append("; ");
            if (pdOld.isShownInUpdateView() != prop.isShownInUpdateView())
                str.append("ShownInUpdate: ").append(renderOldVsNew(renderBool(pdOld.isShownInUpdateView()), renderBool(prop.isShownInUpdateView()))).append("; ");
            if (pdOld.isRecommendedVariable() != prop.isRecommendedVariable())
                str.append("RecommendedVariable: ").append(renderOldVsNew(renderBool(pdOld.isRecommendedVariable()), renderBool(prop.isRecommendedVariable()))).append("; ");
            if (pdOld.isExcludeFromShifting() != prop.isExcludeFromShifting())
                str.append("ExcludedFromShifting: ").append(renderOldVsNew(renderBool(pdOld.isExcludeFromShifting()), renderBool(prop.isExcludeFromShifting()))).append("; ");
            return str.toString();
        }

        private String renderCheckingBlank(String value)
        {
            return StringUtils.isNotBlank(value) ? value : "<none>";
        }

        private static String renderValidators(PropertyDescriptor prop)
        {
             Collection<PropertyValidator> validators = DomainPropertyManager.get().getValidators(prop);
             if (validators.isEmpty())
                 return "<none>";
             List<String> strings = new ArrayList<>();
             validators.forEach(validator -> strings.add(validator.getName() + " [" +
                     StringUtils.replace(PropertyService.get().getValidatorKind(validator.getTypeURI()).getName(), " Property Validator", "") + "]")
             );
             return StringUtils.join(strings, ", ");
        }

        private static String renderConditionalFormats(PropertyDescriptor prop)
        {
            List<ConditionalFormat> formats = DomainPropertyManager.get().getConditionalFormats(prop);
            if (formats.isEmpty())
                return "<none>";
            return Integer.toString(formats.size());
        }

        private void renderValidatorsDiff(DomainProperty prop, String oldValidators, StringBuilder str)
        {
            String validators = renderValidators(prop.getPropertyDescriptor());
            if (!StringUtils.equals(oldValidators, validators))
                str.append("Validators: ").append("old: ").append(oldValidators).append(", new: ").append(validators).append("; ");
        }

        private void renderConditionalFormatsDiff(DomainProperty prop, String oldFormats, StringBuilder str)
        {
            String formats = renderConditionalFormats(prop.getPropertyDescriptor());
            if (!StringUtils.equals(oldFormats, formats))
                str.append("ConditionalFormats: ").append("old: ").append(oldFormats).append(", new: ").append(formats).append("; ");
        }

        private String renderImportAliases(PropertyDescriptor prop)
        {
            Set<String> aliases = prop.getImportAliasSet();
            if (aliases.isEmpty())
                return "<none>";
            return StringUtils.join(aliases, ",");
        }

        private void renderImportAliasesDiff(DomainProperty prop, PropertyDescriptor pdOld, StringBuilder str)
        {
            String oldAliases = renderImportAliases(pdOld);
            String aliases = renderImportAliases(prop.getPropertyDescriptor());
            if (!StringUtils.equals(oldAliases, aliases))
                str.append("ImportAliases: ").append("old: ").append(oldAliases).append(", new: ").append(aliases).append("; ");
        }

        private String renderDefaultValueType(PropertyDescriptor prop)
        {
            DefaultValueType type = prop.getDefaultValueTypeEnum();
            if (null == type)
                return "<none>";
            return type.getLabel();
        }

        private void renderDefaultValueTypeDiff(DomainProperty prop, PropertyDescriptor pdOld, StringBuilder str)
        {
            if (pdOld.getDefaultValueTypeEnum() != prop.getDefaultValueTypeEnum())
                str.append("DefaultValueType: ").append("old: ").append(renderDefaultValueType(pdOld)).append(", new: ")
                   .append(renderDefaultValueType(prop.getPropertyDescriptor())).append("; ");
        }

        private String renderBool(boolean value)
        {
            return value ? "true" : "false";
        }

        private String renderOldVsNew(String oldVal, String newVal)
        {
            return oldVal + " -> " + newVal;
        }

        private void renderLookupDiff(PropertyDescriptor pdNew, PropertyDescriptor pdOld, StringBuilder str)
        {
            str.append("Lookup: [");
            if (!StringUtils.equals(pdOld.getLookupContainer(), pdNew.getLookupContainer()))
                str.append("Container: ").append("old: ").append(getContainerName(pdOld.getLookupContainer())).append(", new: ")
                   .append(getContainerName(pdNew.getLookupContainer())).append(", ");
            if (!StringUtils.equals(pdOld.getLookupSchema(), pdNew.getLookupSchema()))
                str.append("Schema: ").append("old: ").append(pdOld.getLookupSchema()).append(", new: ")
                   .append(pdNew.getLookupSchema()).append(", ");
            if (!StringUtils.equals(pdOld.getLookupQuery(), pdNew.getLookupQuery()))
                str.append("Query: ").append("old: ").append(pdOld.getLookupQuery()).append(", new: ")
                   .append(pdNew.getLookupQuery());
            str.append("]; ");
        }

        private String getContainerName(String containerId)
        {
            if (null != containerId)
            {
                Container container = ContainerManager.getForId(containerId);
                if (null != container)
                    return container.getName();
            }
            return null;
        }
    }

    public Map<String, DomainProperty> createImportMap(boolean includeMVIndicators)
    {
        List<DomainProperty> properties = new ArrayList<>(_properties);
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
            _aliasManager = new AliasManager(ExperimentService.get().getSchema());
            DomainKind k = getDomainKind();
            if (null != k)
            {
                for (PropertyStorageSpec s : k.getBaseProperties(this))
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

        // Keep the names the same if short enough,
        // But always leave room for MV suffix in case it's changed to MV later
        String storage = null;
        if (pd.getName().length() + OntologyManager.MV_INDICATOR_SUFFIX.length() + 1 < 60)
            storage = _aliasManager.decideAlias(pd.getName(), pd.getName());
        else
            storage = _aliasManager.decideAlias(pd.getName(), OntologyManager.MV_INDICATOR_SUFFIX.length() + 1);
        pd.setStorageColumnName(storage);
    }

    public boolean isProvisioned()
    {
        return getStorageTableName() != null && getDomainKind().getStorageSchemaName() != null;
    }

    @Override
    @Nullable
    public TemplateInfo getTemplateInfo()
    {
        return _dd.getTemplateInfo();
    }

    /**
     * Find the DomainTemplate used to create this Domain, if it is available.
     */
    @Nullable
    public DomainTemplate getTemplate()
    {
        return DomainTemplate.findTemplate(getTemplateInfo(), getDomainKind().getKindName());
    }
}
