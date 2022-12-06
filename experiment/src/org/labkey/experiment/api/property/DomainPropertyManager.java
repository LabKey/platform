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

    private static class ConditionalFormatLoader implements CacheLoader<Container, List<ConditionalFormatWithPropertyId>>
    {
        @Override
        public List<ConditionalFormatWithPropertyId> load(@NotNull Container container, Object ignored)
        {
            SQLFragment sql = new SQLFragment("SELECT CF.* FROM ");
            sql.append(getExpSchema().getTable("ConditionalFormat"), "CF");
            sql.append(" WHERE CF.PropertyId IN ");
            sql.append("(SELECT PropertyId FROM ");
            sql.append(OntologyManager.getTinfoPropertyDescriptor(), "pd");
            sql.append(" WHERE pd.Container = ?) ORDER BY PropertyId, SortOrder");
            sql.add(container);

            return Collections.unmodifiableList(new SqlSelector(getExpSchema(), sql).getArrayList(ConditionalFormatWithPropertyId.class));
        }
    }

    private static final ConditionalFormatLoader CONDITIONAL_FORMAT_LOADER = new ConditionalFormatLoader();
    private static final DatabaseCache<Container, List<ConditionalFormatWithPropertyId>> CONDITIONAL_FORMAT_CACHE = new DatabaseCache<>(getExpSchema().getScope(), Constants.getMaxContainers(), CacheManager.DAY, "Conditional formats");

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
            List<ConditionalFormatWithPropertyId> containerConditionalFormats = getConditionalFormats(property.getContainer());
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
        return CONDITIONAL_FORMAT_CACHE.get(container, null, CONDITIONAL_FORMAT_LOADER);
    }

    // Container -> PropertyId -> Collection<PropertyValidator>
    private static final CacheLoader<Container, MultiValuedMap<Integer, PropertyValidator>> PV_LOADER = (container, argument) -> {
        /*
         * There are a LOT more property descriptors than property validators, let's just sweep them all up, if we have a container
         * CONSIDER: Should PropertyValidators just be cached as part of the PropertyDescriptor?
         */
        String sql = "SELECT PV.* " +
                "FROM " + getTinfoValidator() + " PV " +
                "WHERE PV.Container=?\n";

        final MultiValuedMap<Integer, PropertyValidator> validators = new ArrayListValuedHashMap<>();

        new SqlSelector(getExpSchema(), sql, container).forEach(PropertyValidator.class, pv -> validators.put(pv.getPropertyId(), pv));

        return validators.isEmpty() ? MultiMapUtils.emptyMultiValuedMap() : MultiMapUtils.unmodifiableMultiValuedMap(validators);
    };

    private static final Cache<Container, MultiValuedMap<Integer, PropertyValidator>> VALIDATOR_CACHE = new BlockingCache<>(new DatabaseCache<>(getExpSchema().getScope(), Constants.getMaxContainers(), CacheManager.HOUR, "Property validators"), PV_LOADER);
    private static final Collection<PropertyValidator> EMPTY_COLLECTION = Collections.emptyList();


    private Collection<PropertyValidator> getValidators(@NotNull Container c, int propertyId)
    {
        if (propertyId == 0)
            return EMPTY_COLLECTION;

        MultiValuedMap<Integer, PropertyValidator> validators = VALIDATOR_CACHE.get(c); // No validators in c -> empty MultiValuedMap
        Collection<PropertyValidator> coll = validators.get(propertyId); // No validators for propertyId -> empty collection
        return Collections.unmodifiableCollection(coll);
    }



    /**
     * Remove a domain property reference to a validator and delete the validator if there are
     * no more references to it.
     */
    void removePropertyValidator(DomainProperty property, IPropertyValidator validator)
    {
        if (property.getPropertyId() != 0)
        {
            SQLFragment deleteValidator = new SQLFragment(
                    "DELETE FROM " + getTinfoValidator() +
                        " WHERE Container = ? AND PropertyId = ? AND RowId = ?",
                    property.getContainer(), property.getPropertyId(), validator.getRowId());
            new SqlExecutor(getExpSchema()).execute(deleteValidator);

            VALIDATOR_CACHE.remove(property.getContainer());
        }
    }


    void savePropertyValidator(User user, DomainProperty property, IPropertyValidator validator) throws ChangePropertyDescriptorException
    {
        if (property.getPropertyId() != 0)
        {
            try
            {
                validator.setPropertyId(property.getPropertyId());
                validator.save(user, property.getContainer());
                VALIDATOR_CACHE.remove(validator.getContainer());
            }
            catch (ValidationException e)
            {
                throw new ChangePropertyDescriptorException(String.format("An error occurred saving the field: '%s'. %s", property.getName(), e.getMessage()));
            }
        }
    }


    public void removeValidatorsForPropertyDescriptor(@NotNull Container c, int descriptorId)
    {
        // find validators associated with this PD, and not associated with others PDs
        SQLFragment deleteValidators = new SQLFragment(
                "DELETE FROM exp.PropertyValidator WHERE Container = ? AND PropertyId = ?",
                c, descriptorId);
        new SqlExecutor(getExpSchema()).execute(deleteValidators);

        VALIDATOR_CACHE.remove(c);
    }


    public void deleteAllValidatorsAndFormats(Container c)
    {
        SqlExecutor executor = new SqlExecutor(getExpSchema());

        SQLFragment validatorSQL = new SQLFragment("DELETE FROM " + getTinfoValidator() + " WHERE Container = ?", c.getId());
        executor.execute(validatorSQL);

        SQLFragment deleteConditionalFormatsSQL = new SQLFragment("DELETE FROM " + getTinfoConditionalFormat() + " WHERE PropertyId IN " +
                "(SELECT PropertyId FROM " + OntologyManager.getTinfoPropertyDescriptor() + " WHERE Container = ?)", c.getId());
        executor.execute(deleteConditionalFormatsSQL);

        VALIDATOR_CACHE.remove(c);
        CONDITIONAL_FORMAT_CACHE.remove(c);
    }


    public void deleteConditionalFormats(int propertyId)
    {
        SQLFragment sql = new SQLFragment("DELETE FROM " + getTinfoConditionalFormat() + " WHERE PropertyId = ?", propertyId);
        new SqlExecutor(getExpSchema()).execute(sql);
        // Blow the cache
        CONDITIONAL_FORMAT_CACHE.clear();
    }

    public static void clearCaches()
    {
        VALIDATOR_CACHE.clear();
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
                CONDITIONAL_FORMAT_CACHE.remove(prop.getContainer());
            }
        }
    }
}