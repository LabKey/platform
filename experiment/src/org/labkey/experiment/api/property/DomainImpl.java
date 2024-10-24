/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.ListofMapsDataIterator;
import org.labkey.api.dataiterator.Pump;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StatementDataIterator;
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
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JdbcUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import static org.labkey.api.data.ColumnRenderPropertiesImpl.STORAGE_UNIQUE_ID_SEQUENCE_PREFIX;

public class DomainImpl implements Domain
{
    public static final String DISABLED_SYSTEM_FIELDS_KEY = "disabledFields";

    boolean _new;
    boolean _enforceStorageProperties = true;
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
        List<DomainPropertyManager.ConditionalFormatWithPropertyId> allFormats = DomainPropertyManager.get().getConditionalFormats(getContainer());

        List<PropertyDescriptor> pds = OntologyManager.getPropertiesForType(getTypeURI(), getContainer());
        _properties = new ArrayList<>();
        if (pds != null)
        {
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

    @Override
    public Object get_Ts()
    {
        return _dd.get_Ts();
    }

    @Override
    public Container getContainer()
    {
        return _dd.getContainer();
    }

    @Override
    public DomainKind<?> getDomainKind()
    {
        return _dd.getDomainKind();
    }

    @Override
    public String getName()
    {
        return _dd.getName();
    }

    @Override
    public String getLabel()
    {
        DomainKind<?> kind = getDomainKind();
        if (kind == null)
        {
            return "Domain '" + getName() + "'";
        }
        else
        {
            return getDomainKind().getTypeLabel(this);
        }
    }

    @Override
    public String getLabel(Container container)
    {
        String ret = getLabel();
        if (!getContainer().equals(container))
        {
            ret += "(" + getContainer().getPath() + ")";
        }
        return ret;
    }


    @Override
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
    @Override
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
    @Override
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

    @Override
    public String getDescription()
    {
        return _dd.getDescription();
    }

    @Override
    public int getTypeId()
    {
        return _dd.getDomainId();
    }

    @Override
    public void setName(String name)
    {
        _dd = _dd.edit().setName(name).build();
    }

    @Override
    public void setDescription(String description)
    {
        _dd = _dd.edit().setDescription(description).build();
    }

    @Override
    @NotNull
    public List<? extends DomainPropertyImpl> getProperties()
    {
        return Collections.unmodifiableList(_properties);
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
    public ActionURL urlShowData(ContainerUser context)
    {
        return getDomainKind().urlShowData(this, context);
    }


    @Override
    public void delete(@Nullable User user) throws DomainNotFoundException
    {
        delete(user, null);
    }
    @Override
    public void delete(@Nullable User user, @Nullable String auditUserComment) throws DomainNotFoundException
    {
        ExperimentService exp = ExperimentService.get();
        Lock domainLock = getLock(_dd);
        try (DbScope.Transaction transaction = exp.getSchema().getScope().ensureTransaction(domainLock))
        {
            DefaultValueService.get().clearDefaultValues(getContainer(), this);
            OntologyManager.deleteDomain(getTypeURI(), getContainer());
            StorageProvisioner.get().drop(this);
            addAuditEvent(user, String.format("The domain %s was deleted", _dd.getName()), auditUserComment);
            DomainPropertyManager.clearCaches();
            transaction.commit();
        }
    }

    private boolean isNew()
    {
        return _new;
    }

    @Override
    public void save(User user) throws ChangePropertyDescriptorException
    {
        save(user, false);
    }

    @Override
    public Lock getDatabaseLock()
    {
        return getLock(_dd);
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

    private void validatePropertyDefaultValue(User user, DomainProperty dp, String value, boolean validateOnly) throws ChangePropertyDescriptorException
    {
        try
        {
            // Will throw ConversionException or IllegalArgumentException if default value does not format correctly or
            // match data type
            DomainUtil.getFormattedDefaultValue(user, dp, value, validateOnly);
        }
        catch (ConversionException | IllegalArgumentException e)
        {
            throw new ChangePropertyDescriptorException("Property " + dp.getName() + ": " + e.getMessage());
        }
    }

    private void validatePropertyName(DomainProperty dp) throws ChangePropertyDescriptorException
    {
        //Issue 15484: because the server will auto-generate MV indicator columns, which can result in naming conflicts we disallow any user-defined field w/ this suffix
        String name = dp.getName();
        if (name != null && name.toLowerCase().endsWith(OntologyManager.MV_INDICATOR_SUFFIX))
        {
            throw new ChangePropertyDescriptorException("Property " + dp.getName() + ": " + "Field name cannot end with the suffix '" + OntologyManager.MV_INDICATOR_SUFFIX);
        }
    }

    private void validatePropertyUrl(DomainProperty dp) throws ChangePropertyDescriptorException
    {
        String url = dp.getURL();
        if (null != url)
        {
            String message;
            try
            {
                message = StringExpressionFactory.validateURL(url);
                if (null == message && null == StringExpressionFactory.createURL(url))
                    message = "Can't parse url: " + url;    // unexpected parse problem
            }
            catch (Exception x)
            {
                message = x.getMessage();
            }
            if (null != message)
            {
                dp.setURL(null);    // or else _copyProperties() will blow up
                throw new ChangePropertyDescriptorException("Property " + dp.getName() + ": " + message);
            }
        }
    }

    private void validatePropertyFormat(DomainProperty dp) throws ChangePropertyDescriptorException
    {
        String format = dp.getFormat();
        String type = "";

        try {
            if (!StringUtils.isEmpty(dp.getFormat()))
            {
                String ptype = dp.getRangeURI();
                if (ptype.equalsIgnoreCase(PropertyType.DATE_TIME.getTypeUri()))
                {
                    type = " for type " + PropertyType.DATE_TIME.getXarName();
                    // Allow special named formats (these would otherwise fail validation)
                    if (!DateUtil.isSpecialNamedFormat(format))
                        FastDateFormat.getInstance(format);
                }
                else if (ptype.equalsIgnoreCase(PropertyType.DOUBLE.getTypeUri()))
                {
                    type = " for type " + PropertyType.DOUBLE.getXarName();
                    new DecimalFormat(format);
                }
                else if (ptype.equalsIgnoreCase(PropertyType.INTEGER.getTypeUri()))
                {
                    type = " for type " + PropertyType.INTEGER.getXarName();
                    new DecimalFormat(format);
                }
            }
        }
        catch (IllegalArgumentException e)
        {
            throw new ChangePropertyDescriptorException("Property " + dp.getName() + ": " + format + " is an illegal format" + type);
        }
    }

    private void validatePropertyLookup(User user, DomainProperty dp) throws ChangePropertyDescriptorException
    {
        if (dp.getLookup() != null)
        {
            Container lookupContainer = dp.getLookup().getContainer();
            SchemaKey schemaKey = dp.getLookup().getSchemaKey();
            String queryName = dp.getLookup().getQueryName();

            if (lookupContainer == null)
            {
                lookupContainer = dp.getContainer();
            }

            UserSchema schema = QueryService.get().getUserSchema(user, lookupContainer, schemaKey);
            if (schema != null)
            {
                TableInfo table = schema.getTable(queryName);
                if (table != null)
                {
                    List<String> pks = table.getPkColumnNames();
                    String pkCol = pks.get(0);
                    if ((pkCol.equalsIgnoreCase("container") || pkCol.equalsIgnoreCase("containerid")) && pks.size() == 2)
                    {
                        pkCol = pks.get(1);
                    }
                    if (pkCol != null)
                    {
                        ColumnInfo pkColumnInfo = table.getColumn(pkCol);
                        if (!dp.getPropertyType().getJdbcType().equals(pkColumnInfo.getJdbcType()))
                        {
                            throw new ChangePropertyDescriptorException("Property " + dp.getName() + ": Lookup table " + schemaKey + "." + queryName
                                    + " pk does not match property data type. Expected: " + pkColumnInfo.getJdbcType() + ", Actual: " + dp.getPropertyType().getJdbcType());
                        }
                    }
                }
            }
        }
    }

    public void saveIfNotExists(User user) throws ChangePropertyDescriptorException
    {
        save(user, false, true, null);
    }

    @Override
    public void save(User user, boolean allowAddBaseProperty) throws ChangePropertyDescriptorException
    {
        save(user, false, false, null);
    }

    @Override
    public void save(User user, @Nullable String auditComment) throws ChangePropertyDescriptorException
    {
        save(user, false, false, auditComment);
    }

    public void save(User user, boolean allowAddBaseProperty, boolean saveOnlyIfNotExists, @Nullable String auditComment) throws ChangePropertyDescriptorException
    {
        ExperimentService exp = ExperimentService.get();

        // NOTE: the synchronization here does not remove the need to add better synchronization in StorageProvisioner, but it helps
        Lock domainLock = getLock(_dd);

        TableInfo tableDD = OntologyManager.getTinfoDomainDescriptor();
        DbSchema schema = tableDD.getSchema();
        DbScope scope = schema.getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction(domainLock))
        {
            // This is a pretty heavy-handed way to fix a deadlock problem, but it works
            // CONSIDER: another approach might be to fine tune the filters/indexes used in the Table.insert/OntologyManager.getDomainDescriptor calls
            // or using LSID as the primary key on DomainDescriptor?
            if (scope.getSqlDialect().isSqlServer())
            {
                String sql = "SELECT * FROM " + OntologyManager.getTinfoDomainDescriptor() + " WITH (UPDLOCK)";
                new SqlSelector(schema, sql).getArrayList(DomainDescriptor.class);
            }

            List<DomainProperty> checkRequiredStatus = new ArrayList<>();
            boolean isDomainNew = isNew();         // #32406 Need to capture because _new changes during the process
            if (saveOnlyIfNotExists && !isDomainNew)
                throw new IllegalStateException();
            if (!isDomainNew || saveOnlyIfNotExists)
            {
                DomainDescriptor ddCheck = OntologyManager.getDomainDescriptor(_dd.getDomainId());
                if (saveOnlyIfNotExists && null != ddCheck)
                {
                    transaction.commit();
                    return;
                }
                if (!isDomainNew && !JdbcUtil.rowVersionEqual(ddCheck.get_Ts(), _dd.get_Ts()))
                    throw new OptimisticConflictException("Domain has been updated by another user or process.", Table.SQLSTATE_TRANSACTION_STATE, 0);
            }

            // call OntologyManager method to invalidate proper caches
            _dd = OntologyManager.ensureDomainDescriptor(_dd);

            boolean propChanged = false;
            int sortOrder = 0;

            List<DomainProperty> propsDropped = new ArrayList<>();
            List<DomainProperty> propsAdded = new ArrayList<>();

            DomainKind<?> kind = getDomainKind();
            boolean hasProvisioner = null != kind && null != kind.getStorageSchemaName();

            // Certain provisioned table types (Lists and Datasets) get wiped when their fields are replaced via field Import
            if (hasProvisioner && isShouldDeleteAllData())
            {
                try
                {
                    TableInfo tableInfo = kind.getTableInfo(user, getContainer(), getName(), null);
                    if (tableInfo == null)
                    {
                        throw new ChangePropertyDescriptorException("Couldn't resolve TableInfo for domain kind " + kind + " with name " + getName());
                    }
                    QueryUpdateService update = tableInfo.getUpdateService();
                    if (update == null)
                    {
                        throw new ChangePropertyDescriptorException("Couldn't resolve QueryUpdateService for domain kind " + kind + " with name " + getName());
                    }
                    update.truncateRows(user, getContainer(), null, null);
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
                    StorageProvisionerImpl.get().dropProperties(this, propsDropped);
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

                    if (impl.isRecreateRequired() && !impl.isSystemPropertySwap())
                    {
                        impl.markAsNew();
                    }

                    if (impl.isSystemPropertySwap())
                    {
                        // Property descriptor was swapped for a different pd
                        propChanged = true;
                    }
                    else if (impl.isNew())
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
                                checkAndThrowSizeConstraints(kind, impl);
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

                    validatePropertyName(impl);
                    validatePropertyUrl(impl);

                    if (impl.getDefaultValue() != null)
                    {
                        validatePropertyDefaultValue(user, impl, impl.getDefaultValue(), true);
                    }

                    if (impl.getFormat() != null)
                    {
                        validatePropertyFormat(impl);
                    }

                    if (getDomainKind() != null && getDomainKind().ensurePropertyLookup())
                    {
                        validatePropertyLookup(user, impl);
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
                    {
                        PropertyChangeAuditInfo auditInfo = new PropertyChangeAuditInfo(impl, pdOld, oldValidators, oldFormats);
                        if (auditInfo.isChanged())
                            propertyAuditInfo.add(auditInfo);
                    }
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
            if (hasProvisioner)
            {
                if (propChanged && _enforceStorageProperties)
                {
                    if (!propsAdded.isEmpty())
                    {
                        StorageProvisionerImpl.get().addProperties(this, propsAdded, allowAddBaseProperty);
                        try
                        {
                            ensureUniqueIdValues(propsAdded);
                        }
                        catch (Exception e)
                        {
                            throw new ChangePropertyDescriptorException(e);
                        }
                    }
                }

                // ensure that the provisioned table is created if we have base properties.
                // The domain may not have any non-base properties -- e.g. the "Study Specimens" SampleSets
                if (!baseProperties.isEmpty())
                {
                    StorageProvisioner.get().ensureStorageTable(this, kind, exp.getSchema().getScope());
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

            final boolean finalPropChanged = propChanged;
            final String extraAuditComment = auditComment == null ? "" : auditComment + ' ';

            // Move audit event creation to outside the transaction to avoid deadlocks involving audit storage table creation
            Runnable afterDomainCommit = () ->
            {
                if (isDomainNew)
                    addAuditEvent(user, extraAuditComment + String.format("The domain %s was created", _dd.getName()), null);

                if (finalPropChanged)
                {
                    final Long domainEventId = addAuditEvent(user, extraAuditComment + String.format("The column(s) of domain %s were modified", _dd.getName()), null);
                    propertyAuditInfo.forEach(auditInfo -> addPropertyAuditEvent(user, auditInfo.getProp(), auditInfo.getAction(), domainEventId, getName(), auditInfo.getDetails()));
                }
                else if (!isDomainNew)
                {
                    addAuditEvent(user, extraAuditComment + String.format("The descriptor of domain %s was updated", _dd.getName()), null);
                }
            };
            transaction.addCommitTask(afterDomainCommit, DbScope.CommitTaskOption.POSTCOMMIT);

            Runnable afterDomainCommitOrRollback = () ->
            {
                // Even if no storage table schema changes occurred, we want to invalidate table to pick up an metadata changes
                // Invalidate even if !propChanged, because ordering might have changed (#25296)
                OntologyManager.invalidateDomain(this);
                if (getDomainKind() != null)
                    getDomainKind().invalidate(this);
            };
            transaction.addCommitTask(afterDomainCommitOrRollback, DbScope.CommitTaskOption.POSTCOMMIT, DbScope.CommitTaskOption.POSTROLLBACK);

            QueryService.get().updateLastModified();
            transaction.commit();
        }
    }

    private void ensureUniqueIdValues(List<DomainProperty> propsAdded) throws SQLException, BatchValidationException
    {
        SchemaTableInfo table = StorageProvisioner.get().getSchemaTableInfo(this);
        DbScope scope = table.getSchema().getScope();
        SqlDialect dialect = table.getSqlDialect();

        List<DomainProperty> uniqueIdProps = propsAdded.stream().filter(DomainProperty::isUniqueIdField).toList();
        if (uniqueIdProps.isEmpty())
            return;

        Set<ColumnInfo> uniqueIndexCols = new LinkedHashSet<>();
        // Find the uniqueIndexCols so we can use these for selecting items to update the uniqueIds of,
        // but exclude the uniqueId fields themselves.
        table.getUniqueIndices().values().forEach(idx -> idx.second.stream().filter(col -> !col.isUniqueIdField()).forEach(uniqueIndexCols::add));

        DbSequence sequence = DbSequenceManager.get(ContainerManager.getRoot(), STORAGE_UNIQUE_ID_SEQUENCE_PREFIX);

        TableSelector selector = new TableSelector(table, uniqueIndexCols, null, null);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Results results = selector.getResults())
        {
            results.forEach(rowKeys -> {
                Map<String, Object> newRow = new CaseInsensitiveHashMap<>();
                newRow.putAll(rowKeys);
                uniqueIdProps.forEach(prop -> newRow.put(prop.getName(), SimpleTranslator.TextIdColumn.getFormattedValue(sequence.next())));
                rows.add(newRow);
            });
        }
        DataIteratorContext ctx = new DataIteratorContext();
        Set<String> colNames = new HashSet<>();
        uniqueIndexCols.forEach(col ->
                colNames.add(col.getName()));
        uniqueIdProps.forEach(prop -> colNames.add(prop.getName()));
        ListofMapsDataIterator mapsDi = new ListofMapsDataIterator(colNames, rows);
        mapsDi.setDebugName(table.getName() + ".ensureUniqueIds");
        SQLFragment sql = new SQLFragment().append("UPDATE ").append(table).append(" SET ");
        String separator = "";
        for (DomainProperty prop: uniqueIdProps)
        {
            // Issue 50715: quote column names with spaces for update statement
            sql.append(separator).append(prop.getPropertyDescriptor().getLegalSelectName(dialect)).append(" = ?").add(new Parameter(prop.getName(), prop.getJdbcType()));
            separator = ",";
        }
        sql.append(" WHERE ");
        separator = "";
        for (ColumnInfo col: uniqueIndexCols)
        {
            sql.append(separator).append(col.getName()).append(" = ?").add(new Parameter(col.getName(), col.getJdbcType()));
            separator = " AND ";
        }

        DataIteratorBuilder it = context -> {
            try
            {
                ParameterMapStatement stmt1 = new ParameterMapStatement(scope, sql, null);
                return new StatementDataIterator(dialect, mapsDi, ctx, stmt1);
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        };

        Pump p = new Pump(it, ctx);
        p.run();

        if (ctx.getErrors().hasErrors())
            throw ctx.getErrors();
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

    private void checkAndThrowSizeConstraints(DomainKind<?> kind, DomainProperty prop)
    {
        boolean tooLong = kind.exceedsMaxLength(this, prop);
        if (tooLong)
        {
            throw new IllegalStateException("The property \"" + prop.getName() + "\" cannot be scaled down. It contains existing values exceeding ["+ prop.getScale() + "] characters.");
        }
    }

    private Long addAuditEvent(@Nullable User user, String comment, @Nullable String auditUserComment)
    {
        if (user != null)
        {
            DomainAuditProvider.DomainAuditEvent event = new DomainAuditProvider.DomainAuditEvent(getContainer().getId(), comment);
            event.setUserComment(auditUserComment);

            if (_dd.getProject() != null)
                event.setProjectId(_dd.getProject().getId());

            event.setDomainUri(getTypeURI());
            event.setDomainName(getName());

            AuditTypeEvent retEvent = AuditLogService.get().addEvent(user, event);
            return null != retEvent ? retEvent.getRowId() : null;
        }
        return null;
    }

    private void addPropertyAuditEvent(@Nullable User user, DomainProperty prop, String action, Long domainEventId, String domainName, String comment)
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

        public boolean isChanged() { return !_details.isEmpty(); }

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
                str.append("Schema: ").append(lookup.getSchemaKey()).append(", ")
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
            str.append("Scannable: ").append(renderBool(prop.isScannable())).append("; ");
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
            if (pdOld.isShownInLookupView() != prop.isShownInLookupView())
                str.append("ShownInLookupView: ").append(renderOldVsNew(renderBool(pdOld.isShownInLookupView()), renderBool(prop.isShownInLookupView()))).append("; ");
            if (pdOld.isRecommendedVariable() != prop.isRecommendedVariable())
                str.append("RecommendedVariable: ").append(renderOldVsNew(renderBool(pdOld.isRecommendedVariable()), renderBool(prop.isRecommendedVariable()))).append("; ");
            if (pdOld.isExcludeFromShifting() != prop.isExcludeFromShifting())
                str.append("ExcludedFromShifting: ").append(renderOldVsNew(renderBool(pdOld.isExcludeFromShifting()), renderBool(prop.isExcludeFromShifting()))).append("; ");
            if (pdOld.isScannable() != prop.isScannable())
                str.append("Scannable: ").append(renderOldVsNew(renderBool(pdOld.isScannable()), renderBool(prop.isScannable()))).append("; ");
            if (!StringUtils.equals(pdOld.getDerivationDataScope(), prop.getDerivationDataScope()))
                str.append("DerivationDataScope: ").append(renderOldVsNew(renderCheckingBlank(pdOld.getDerivationDataScope()), renderCheckingBlank(prop.getDerivationDataScope()))).append("; ");
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

    @Override
    public Map<String, DomainProperty> createImportMap(boolean includeMVIndicators)
    {
        List<DomainProperty> properties = new ArrayList<>(_properties);
        return ImportAliasable.Helper.createImportMap(properties, includeMVIndicators);
    }

    @Override
    public DomainProperty addPropertyOfPropertyDescriptor(PropertyDescriptor pd)
    {
        assert pd.getPropertyId() == 0;
        assert pd.getContainer().equals(getContainer());

        // Warning: Shallow copy
        DomainPropertyImpl ret = new DomainPropertyImpl(this, pd.clone());
        _properties.add(ret);
        return ret;
    }

    @Override
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

    @Override
    public DomainProperty addProperty(PropertyStorageSpec spec)
    {
        PropertyDescriptor pd = new PropertyDescriptor();
        pd.setContainer(getContainer());
        pd.setDatabaseDefaultValue(spec.getDefaultValue());
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

    @Override
    public String getTypeURI()
    {
        return _dd.getDomainURI();
    }

    @Override
    public DomainPropertyImpl getProperty(int id)
    {
        for (DomainPropertyImpl prop : getProperties())
        {
            if (prop.getPropertyId() == id)
                return prop;
        }
        return null;
    }

    @Override
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

    @Override
    public DomainPropertyImpl getPropertyByName(String name)
    {
        for (DomainPropertyImpl prop : getProperties())
        {
            if (prop.getName().equalsIgnoreCase(name))
                return prop;
        }
        return null;
    }

    @Override
    public List<BaseColumnInfo> getColumns(TableInfo sourceTable, ColumnInfo lsidColumn, Container container, User user)
    {
        List<BaseColumnInfo> result = new ArrayList<>();
        for (DomainProperty property : getProperties())
        {
            var column = new PropertyColumn(property.getPropertyDescriptor(), lsidColumn, container, user, false);
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
        return (_dd.equals(((DomainImpl) obj)._dd));
    }

    @Override
    public String toString()
    {
        return getTypeURI();
    }

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys()
    {
        return _propertyForeignKeys;
    }

    @Override
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

    @Override
    public void setPropertyIndices(@NotNull Set<PropertyStorageSpec.Index> propertyIndices)
    {
        _propertyIndices = propertyIndices;
    }

    @Override
    public void setPropertyIndices(@NotNull List<GWTIndex> indices, @Nullable Set<String> lowerReservedNames)
    {
        Set<PropertyStorageSpec.Index> propertyIndices = new HashSet<>();
        for (GWTIndex index : indices)
        {
            // issue 25273: verify that each index column name exists in the domain
            if (lowerReservedNames != null)
            {
                for (String indexColName : index.getColumnNames())
                {
                    if (!lowerReservedNames.contains(indexColName.toLowerCase()) && getPropertyByName(indexColName) == null)
                        throw new IllegalArgumentException("Index column name '" + indexColName + "' does not exist.");
                }
            }

            PropertyStorageSpec.Index propIndex = new PropertyStorageSpec.Index(index.isUnique(), index.getColumnNames());
            propertyIndices.add(propIndex);
        }
        setPropertyIndices(propertyIndices);
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
        return getDomainKind().isDeleteAllDataOnFieldImport() && _shouldDeleteAllData;
    }

    public void generateStorageColumnName(PropertyDescriptor pd)
    {
        if (null == _aliasManager)
        {
            _aliasManager = new AliasManager(ExperimentService.get().getSchema());
            DomainKind<?> k = getDomainKind();
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
        final String storage;
        if (pd.getName().length() + OntologyManager.MV_INDICATOR_SUFFIX.length() + 1 < 60)
            storage = _aliasManager.decideAlias(pd.getName(), pd.getName());
        else
            storage = _aliasManager.decideAlias(pd.getName(), OntologyManager.MV_INDICATOR_SUFFIX.length() + 1);
        pd.setStorageColumnName(storage);
    }

    @Override
    public boolean isProvisioned()
    {
        DomainKind<?> domainKind = getDomainKind();
        return getStorageTableName() != null && domainKind != null && domainKind.getStorageSchemaName() != null;
    }

    public String getSystemFieldConfig()
    {
        return _dd.getSystemFieldConfig();
    }

    public void setSystemFieldConfig(String systemFieldConfig)
    {
        _dd = _dd.edit().setSystemFieldConfig(systemFieldConfig).build();
    }

    @Override
    public List<String> getDisabledSystemFields()
    {
        List<String> disabledSystemFields = new ArrayList<>();

        String systemFieldConfigStr = getSystemFieldConfig();
        if (StringUtils.isEmpty(systemFieldConfigStr))
            return disabledSystemFields;

        JSONObject configJson = new JSONObject(systemFieldConfigStr);
        JSONArray disabledFields = configJson.optJSONArray(DISABLED_SYSTEM_FIELDS_KEY);
        if (disabledFields == null)
            return disabledSystemFields;

        for (int i = 0; i < disabledFields.length(); i++)
            disabledSystemFields.add(disabledFields.optString(i));

        DomainKind domainKind = getDomainKind();
        if (domainKind != null)
            return domainKind.getDisabledSystemFields(disabledSystemFields);

        return disabledSystemFields;
    }

    @Override
    public void setDisabledSystemFields(@Nullable List<String> disabledSystemFields)
    {
        boolean emptyFields = disabledSystemFields == null || disabledSystemFields.isEmpty();
        String systemFieldConfigStr = getSystemFieldConfig();

        JSONObject configJson;
        if (StringUtils.isEmpty(systemFieldConfigStr))
        {
            if (emptyFields)
                return;
            configJson = new JSONObject();
        }
        else
            configJson = new JSONObject(systemFieldConfigStr);

        JSONArray existingDisabled = configJson.optJSONArray(DISABLED_SYSTEM_FIELDS_KEY);
        if (emptyFields && existingDisabled == null)
            return;

        JSONArray fields = new JSONArray(disabledSystemFields);
        configJson.put(DISABLED_SYSTEM_FIELDS_KEY, fields);

        setSystemFieldConfig(configJson.toString());
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
