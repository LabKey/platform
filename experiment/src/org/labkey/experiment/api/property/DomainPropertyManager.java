/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.Constants;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
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
import org.labkey.experiment.api.ExperimentServiceImpl;

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

    private static class ConditionalFormatLoader implements CacheLoader<String, List<ConditionalFormatWithPropertyId>>
    {
        @Override
        public List<ConditionalFormatWithPropertyId> load(String key, Object containerId)
        {
            SQLFragment sql = new SQLFragment("SELECT CF.* FROM ");
            sql.append(getExpSchema().getTable("ConditionalFormat"), "CF");
            sql.append(" WHERE CF.PropertyId IN ");
            sql.append("(SELECT PropertyId FROM ");
            sql.append(OntologyManager.getTinfoPropertyDescriptor(), "pd");
            sql.append(" WHERE pd.Container = ?) ORDER BY PropertyId, SortOrder");
            sql.add(containerId);

            return Collections.unmodifiableList(new SqlSelector(getExpSchema(), sql).getArrayList(ConditionalFormatWithPropertyId.class));
        }
    }

    private static final ConditionalFormatLoader CONDITIONAL_FORMAT_LOADER = new ConditionalFormatLoader();
    private static final DatabaseCache<List<ConditionalFormatWithPropertyId>> CONDITIONAL_FORMAT_CACHE = new DatabaseCache<>(getExpSchema().getScope(), CacheManager.UNLIMITED, CacheManager.DAY, "ConditionalFormats");

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
            List<ConditionalFormatWithPropertyId> containerConditionalFormats = CONDITIONAL_FORMAT_CACHE.get(property.getContainer().getId(), property.getContainer(), CONDITIONAL_FORMAT_LOADER);
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
        return CONDITIONAL_FORMAT_CACHE.get(container.getId(), container, CONDITIONAL_FORMAT_LOADER);
    }

    // Container.getId() -> PropertyId -> Collection<PropertyValidator>
    private static final CacheLoader<String, MultiValuedMap<Integer, PropertyValidator>> PV_LOADER = (containerId, argument) -> {
        /*
         * There are a LOT more property descriptors than property validators, let's just sweep them all up, if we have a container
         * CONSIDER: Should PropertyValidators just be cached as part of the PropertyDescriptor?
         */
        String sql = "SELECT VR.PropertyId, PV.* " +
                "FROM " + getTinfoValidatorReference() + " VR " +
                "LEFT OUTER JOIN " + getTinfoValidator() + " PV ON (VR.ValidatorId = PV.RowId) " +
                "WHERE PV.Container=?\n";

        final MultiValuedMap<Integer, PropertyValidator> validators = new ArrayListValuedHashMap<>();

        new SqlSelector(getExpSchema(), sql, containerId).forEach(pv -> validators.put(pv.getPropertyId(), pv), PropertyValidator.class);

        return validators.isEmpty() ? MultiMapUtils.emptyMultiValuedMap() : MultiMapUtils.unmodifiableMultiValuedMap(validators);
    };

    private static final Cache<String, MultiValuedMap<Integer, PropertyValidator>> VALIDATOR_CACHE = new BlockingCache<>(new DatabaseCache<>(getExpSchema().getScope(), Constants.getMaxContainers(), CacheManager.HOUR, "Property Validators"), PV_LOADER);
    private static final Collection<PropertyValidator> EMPTY_COLLECTION = Collections.emptyList();


    private Collection<PropertyValidator> getValidators(@NotNull Container c, int propertyId)
    {
        if (propertyId == 0)
            return EMPTY_COLLECTION;

        MultiValuedMap<Integer, PropertyValidator> validators = VALIDATOR_CACHE.get(c.getId());
        if (null == validators)
            return EMPTY_COLLECTION;
        Collection<PropertyValidator> coll = validators.get(propertyId);
        if (null == coll)
            return EMPTY_COLLECTION;
        else
            return Collections.unmodifiableCollection(coll);
    }



    /**
     * Remove a domain property reference to a validator and delete the validator if there are
     * no more references to it.
     */
    void removePropertyValidator(User user, DomainProperty property, IPropertyValidator validator)
    {
        if (property.getPropertyId() != 0)
        {
            _removeValidatorReference(property.getPropertyId(), validator.getRowId());

            String sql = "DELETE FROM " + getTinfoValidator() +
                        " WHERE RowId = ?" +
                        " AND NOT EXISTS (SELECT * FROM " + getTinfoValidatorReference() + " VR " +
                            " WHERE  VR.ValidatorId = ?)";
            new SqlExecutor(getExpSchema()).execute(sql, validator.getRowId(), validator.getRowId());
            VALIDATOR_CACHE.remove(property.getContainer().getId());
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


    void savePropertyValidator(User user, DomainProperty property, IPropertyValidator validator) throws ChangePropertyDescriptorException
    {
        if (property.getPropertyId() != 0)
        {
            try
            {
                addValidatorReference(property, validator.save(user, property.getContainer()));
                VALIDATOR_CACHE.remove(validator.getContainer().getId());
            }
            catch (ValidationException e)
            {
                throw new ChangePropertyDescriptorException(String.format("An error occurred saving the field: '%s'. %s", property.getName(), e.getMessage()));
            }
        }
    }


    private void addValidatorReference(DomainProperty property, IPropertyValidator validator)
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
        VALIDATOR_CACHE.remove(c.getId());
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

        VALIDATOR_CACHE.remove(c.getId());
        CONDITIONAL_FORMAT_CACHE.remove(c.getId());
    }


    public void deleteConditionalFormats(int propertyId)
    {
        SQLFragment sql = new SQLFragment("DELETE FROM " + getTinfoConditionalFormat() + " WHERE PropertyId = ?", propertyId);
        new SqlExecutor(getExpSchema()).execute(sql);
        // Blow the cache
        CONDITIONAL_FORMAT_CACHE.clear();
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
                CONDITIONAL_FORMAT_CACHE.remove(prop.getContainer().getId());
            }
        }
    }
}