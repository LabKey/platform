/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.Wrapper;
import org.labkey.api.collections.UnmodifiableMultiMap;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* User: Karl Lum
* Date: Aug 14, 2008
* Time: 9:46:07 AM
*/
public class DomainPropertyManager
{
    private static final DomainPropertyManager _instance = new DomainPropertyManager();

    private BlockingCache<GUID, List<ConditionalFormatWithPropertyId>> _conditionalFormatCache = CacheManager.getBlockingCache(500, CacheManager.DAY, "ConditionalFormats", new CacheLoader<GUID, List<ConditionalFormatWithPropertyId>>()
    {
        @Override
        public List<ConditionalFormatWithPropertyId> load(GUID containerId, @Nullable Object argument)
        {
            SQLFragment sql = new SQLFragment("SELECT CF.* FROM ");
            sql.append(getTinfoConditionalFormat(), "CF");
            sql.append(" WHERE CF.PropertyId IN ");
            sql.append("(SELECT PropertyId FROM ");
            sql.append(OntologyManager.getTinfoPropertyDescriptor(), "pd");
            sql.append(" WHERE pd.Container = ?) ORDER BY PropertyId, SortOrder");
            sql.add(containerId);

            return Collections.unmodifiableList(new SqlSelector(getExpSchema(), sql).getArrayList(ConditionalFormatWithPropertyId.class));
        }
    });

    private DomainPropertyManager(){}

    public static DomainPropertyManager get()
    {
        return _instance;
    }

    private static DbSchema getExpSchema()
    {
        return ExperimentServiceImpl.get().getExpSchema();
    }

    static public TableInfo getTinfoValidator()
    {
        return getExpSchema().getTable("PropertyValidator");
    }

    public TableInfo getTinfoPropertyDescriptor()
    {
        return getExpSchema().getTable("PropertyDescriptor");
    }

    static public TableInfo getTinfoValidatorReference()
    {
        return getExpSchema().getTable("ValidatorReference");
    }

    public TableInfo getTinfoConditionalFormat()
    {
        return getExpSchema().getTable("ConditionalFormat");
    }

    public Collection<PropertyValidator> getValidators(DomainProperty property)
    {
        return getValidators(property.getContainer(), property.getPropertyId());
    }

    public Collection<PropertyValidator> getValidators(PropertyDescriptor property)
    {
        return getValidators(property.getContainer(), property.getPropertyId());
    }

    public List<ConditionalFormat> getConditionalFormats(DomainPropertyImpl property)
    {
        return getConditionalFormats(property._pd);
    }

    public @NotNull List<ConditionalFormat> getConditionalFormats(PropertyDescriptor property)
    {
        List<ConditionalFormat> result = new ArrayList<>();
        if (property != null && property.getPropertyId() != 0)
        {
            List<ConditionalFormatWithPropertyId> containerConditionalFormats = _conditionalFormatCache.get(property.getContainer().getEntityId());
            for (ConditionalFormatWithPropertyId containerConditionalFormat : containerConditionalFormats)
            {
                if (containerConditionalFormat.getPropertyId() == property.getPropertyId())
                {
                    result.add(containerConditionalFormat);
                }
            }
        }
        return result;
    }

    public static class ConditionalFormatWithPropertyId extends ConditionalFormat
    {
        private int _propertyId;

        public int getPropertyId()
        {
            return _propertyId;
        }

        public void setPropertyId(int propertyId)
        {
            _propertyId = propertyId;
        }
    }

    public List<ConditionalFormatWithPropertyId> getConditionalFormats(Container container)
    {
        return _conditionalFormatCache.get(container.getEntityId());
    }

    private String getCacheKey(int propertyId)
    {
        return String.valueOf(propertyId);
    }



    // Container.getId() -> PropertyId -> Collection<PropertyValidator>
    private static final CacheLoader<String, MultiMap<Integer, PropertyValidator>> PV_LOADER = new CacheLoader<String, MultiMap<Integer, PropertyValidator>>()
    {
        @Override
        public MultiMap<Integer, PropertyValidator> load(String containerId, @Nullable Object argument)
        {
            /*
             * There are a LOT more property descriptors than property validators, let's just sweep them all up, if we have a container
             * CONSIDER: Should PropertyValidators just be cached as part of the PropertyDescriptor?
             */
            String sql = "SELECT VR.PropertyId, PV.* " +
                    "FROM " + getTinfoValidatorReference() + " VR " +
                    "LEFT OUTER JOIN " + getTinfoValidator() + " PV ON (VR.ValidatorId = PV.RowId) " +
                    "WHERE PV.Container=?\n";

            final MultiMap<Integer, PropertyValidator> validators = new MultiHashMap<>();

            new SqlSelector(getExpSchema(), sql, containerId).forEach(new Selector.ForEachBlock<PropertyValidator>()
            {
                @Override
                public void exec(PropertyValidator pv) throws SQLException
                {
                    validators.put(pv.getPropertyId(), pv);
                }
            }, PropertyValidator.class);

            return validators.isEmpty() ? _emptyMap : new UnmodifiableMultiMap<>(validators);
        }
    };

    private static final Cache<String, MultiMap<Integer, PropertyValidator>> validatorCache = new BlockingCache<>(new DatabaseCache<Wrapper<MultiMap<Integer, PropertyValidator>>>(getExpSchema().getScope(), 5000, CacheManager.HOUR, "Property Validators"), PV_LOADER);
    private static final Collection<PropertyValidator> _emptyCollection = Collections.emptyList();
    private static final MultiMap<Integer, PropertyValidator> _emptyMap = new UnmodifiableMultiMap<>(new MultiHashMap<Integer, PropertyValidator>());


    private Collection<PropertyValidator> getValidators(@NotNull Container c, int propertyId)
    {
        if (propertyId == 0)
            return _emptyCollection;

        MultiMap<Integer, PropertyValidator> validators = validatorCache.get(c.getId());
        if (null == validators)
            return _emptyCollection;
        Collection<PropertyValidator> coll = validators.get(propertyId);
        if (null == coll)
            return _emptyCollection;
        else
            return Collections.unmodifiableCollection(coll);
    }



    /**
     * Remove a domain property reference to a validator and delete the validator if there are
     * no more references to it.
     */
    public void removePropertyValidator(User user, DomainProperty property, IPropertyValidator validator)
    {
        if (property.getPropertyId() != 0)
        {
            _removeValidatorReference(property.getPropertyId(), validator.getRowId());

            String sql = "DELETE FROM " + getTinfoValidator() +
                        " WHERE RowId = ?" +
                        " AND NOT EXISTS (SELECT * FROM " + getTinfoValidatorReference() + " VR " +
                            " WHERE  VR.ValidatorId = ?)";
            new SqlExecutor(getExpSchema()).execute(sql, validator.getRowId(), validator.getRowId());
            validatorCache.remove(property.getContainer().getId());
        }
    }


    private void _removePropertyValidator(int propertyId, int validatorId)
    {
        if (propertyId != 0)
        {
            _removeValidatorReference(propertyId, validatorId);

            String sql = "DELETE FROM " + getTinfoValidator() +
                        " WHERE RowId = ?" +
                        " AND NOT EXISTS (SELECT * FROM " + getTinfoValidatorReference() + " VR " +
                            " WHERE  VR.ValidatorId = ?)";
            new SqlExecutor(getExpSchema()).execute(sql, validatorId, validatorId);
        }
    }


    public void savePropertyValidator(User user, DomainProperty property, IPropertyValidator validator) throws ChangePropertyDescriptorException
    {
        if (property.getPropertyId() != 0)
        {
            try
            {
                addValidatorReference(property, validator.save(user, property.getContainer()));
                validatorCache.remove(validator.getContainer().getId());
            }
            catch (ValidationException e)
            {
                throw new ChangePropertyDescriptorException(String.format("An error occurred saving the field: '%s'. %s", property.getName(), e.getMessage()));
            }
        }
    }


    public void addValidatorReference(DomainProperty property, IPropertyValidator validator)
    {
        if (property.getPropertyId() != 0 && validator.getRowId() != 0)
        {
            String sql = "SELECT ValidatorId FROM " + getTinfoValidatorReference() + " WHERE ValidatorId=? AND PropertyId=?";
            Integer id = new SqlSelector(getExpSchema(), sql, validator.getRowId(), property.getPropertyId()).getObject(Integer.class);
            if (id == null)
            {
                SQLFragment insertSQL = new SQLFragment();
                insertSQL.append("INSERT INTO ");
                insertSQL.append(getTinfoValidatorReference());
                insertSQL.append(" (ValidatorId,PropertyId) VALUES(?,?)");
                insertSQL.add(validator.getRowId());
                insertSQL.add(property.getPropertyId());

                new SqlExecutor(getExpSchema()).execute(insertSQL);
            }
        }
        else
            throw new IllegalArgumentException("DomainProperty or IPropertyValidator row ID's cannot be null");
    }


    private void _removeValidatorReference(int propertyId, int validatorId)
    {
        if (propertyId != 0 && validatorId != 0)
        {
            String sql = "DELETE FROM " + getTinfoValidatorReference() + " WHERE ValidatorId=? AND PropertyId=?";
            new SqlExecutor(getExpSchema()).execute(sql, validatorId, propertyId);
        }
        else
            throw new IllegalArgumentException("DomainProperty or IPropertyValidator row ID's cannot be null");
    }


    public void removeValidatorsForPropertyDescriptor(@NotNull Container c, int descriptorId)
    {
        for (PropertyValidator pv : getValidators(c, descriptorId))
        {
            _removePropertyValidator(descriptorId, pv.getRowId());
        }
        validatorCache.remove(c.getId());
    }


    public void deleteAllValidatorsAndFormats(Container c)
    {
        SqlExecutor executor = new SqlExecutor(getExpSchema());
        SQLFragment validatorReferenceSQL = new SQLFragment("DELETE FROM " + getTinfoValidatorReference() +
                " WHERE ValidatorId IN (SELECT RowId FROM " + getTinfoValidator() + " WHERE Container = ?)", c.getId());
        executor.execute(validatorReferenceSQL);

        SQLFragment validatorSQL = new SQLFragment("DELETE FROM " + getTinfoValidator() + " WHERE Container = ?", c.getId());
        executor.execute(validatorSQL);

        SQLFragment deleteConditionalFormatsSQL = new SQLFragment("DELETE FROM " + getTinfoConditionalFormat() + " WHERE PropertyId IN " +
                "(SELECT PropertyId FROM " + OntologyManager.getTinfoPropertyDescriptor() + " WHERE Container = ?)", c.getId());
        executor.execute(deleteConditionalFormatsSQL);

        validatorCache.remove(c.getId());
        _conditionalFormatCache.remove(c.getEntityId());
    }


    public void deleteConditionalFormats(int propertyId)
    {
        SQLFragment sql = new SQLFragment("DELETE FROM " + getTinfoConditionalFormat() + " WHERE PropertyId = ?", propertyId);
        new SqlExecutor(getExpSchema()).execute(sql);
        // Blow the cache
        _conditionalFormatCache.clear();
    }


    public void saveConditionalFormats(User user, PropertyDescriptor prop, List<ConditionalFormat> formats)
    {
        if (null != prop)
        {
            // Delete them all first
            deleteConditionalFormats(prop.getPropertyId());

            // Save the new ones
            int index = 0;
            for (ConditionalFormat format : formats)
            {
                // Table has two additional properties that aren't on the bean itself - propertyId and sortOrder
                Map<String, Object> row = new HashMap<>();
                row.put("Bold", format.isBold());
                row.put("Italic", format.isItalic());
                row.put("Strikethrough", format.isStrikethrough());
                row.put("TextColor", format.getTextColor());
                row.put("BackgroundColor", format.getBackgroundColor());
                row.put("Filter", format.getFilter());
                row.put("SortOrder", index++);
                row.put("PropertyId", prop.getPropertyId());

                Table.insert(user, getTinfoConditionalFormat(), row);
                // Blow the cache for the container
                _conditionalFormatCache.remove(prop.getContainer().getEntityId());
            }
        }
    }
}