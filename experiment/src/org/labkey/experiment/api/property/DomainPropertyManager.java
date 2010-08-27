/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.labkey.api.cache.DbCache;
import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.query.ValidationException;

import java.sql.SQLException;
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

    private DomainPropertyManager(){}

    public static DomainPropertyManager get()
    {
        return _instance;
    }

    private static DbSchema getExpSchema()
    {
        return DbSchema.get("exp");
    }

    public TableInfo getTinfoValidator()
    {
        return getExpSchema().getTable("PropertyValidator");
    }

    public TableInfo getTinfoValidatorReference()
    {
        return getExpSchema().getTable("ValidatorReference");
    }

    public TableInfo getTinfoConditionalFormat()
    {
        return getExpSchema().getTable("ConditionalFormat");
    }

    public PropertyValidator[] getValidators(DomainProperty property)
    {
        return getValidators(property.getPropertyId());
    }

    public PropertyValidator[] getValidators(PropertyDescriptor property)
    {
        return getValidators(property.getPropertyId());
    }

    public ConditionalFormat[] getConditionalFormats(DomainProperty property)
    {
        return getConditionalFormats(property.getPropertyId());
    }

    public ConditionalFormat[] getConditionalFormats(PropertyDescriptor property)
    {
        return getConditionalFormats(property.getPropertyId());
    }

    private ConditionalFormat[] getConditionalFormats(int propertyId)
    {
        try
        {
            if (propertyId != 0)
            {
                String cacheKey = getCacheKey(propertyId);
                ConditionalFormat[] formats = (ConditionalFormat[])DbCache.get(getTinfoConditionalFormat(), cacheKey);

                if (formats != null)
                    return formats;

                String sql = "SELECT CF.* " +
                        "FROM " + getTinfoConditionalFormat() + " CF " +
                        "WHERE CF.PropertyId = ? " +
                        "ORDER BY SortOrder";

                formats = Table.executeQuery(getExpSchema(), sql, new Object[]{propertyId}, ConditionalFormat.class);

                DbCache.put(getTinfoConditionalFormat(), cacheKey, formats);
                return formats;
            }
            return new ConditionalFormat[0];
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    private String getCacheKey(int propertyId)
    {
        return String.valueOf(propertyId);
    }

    private PropertyValidator[] getValidators(int propertyId)
    {
        try
        {
            if (propertyId != 0)
            {
                String cacheKey = getCacheKey(propertyId);
                PropertyValidator[] validators = (PropertyValidator[])DbCache.get(getTinfoValidator(), cacheKey);

                if (validators != null)
                    return validators;

                String sql = "SELECT PV.* " +
                        "FROM " + getTinfoValidator() + " PV " +
                        "INNER JOIN " + getTinfoValidatorReference() + " VR ON (PV.RowId = VR.ValidatorId) " +
                        "WHERE VR.PropertyId = ?\n";

                validators = Table.executeQuery(getExpSchema(), sql, new Object[]{propertyId}, PropertyValidator.class);

                DbCache.put(getTinfoValidator(), cacheKey, validators);
                return validators;
            }
            return new PropertyValidator[0];
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    /**
     * Remove a domain property reference to a validator and delete the validator if there are
     * no more references to it.
     */
    public void removePropertyValidator(User user, DomainProperty property, IPropertyValidator validator)
    {
        try
        {
            if (property.getPropertyId() != 0)
            {
                _removeValidatorReference(property.getPropertyId(), validator.getRowId());

                String sql = "DELETE FROM " + getTinfoValidator() +
                            " WHERE RowId = ?" +
                            " AND NOT EXISTS (SELECT * FROM " + getTinfoValidatorReference() + " VR " +
                                " WHERE  VR.ValidatorId = ?)";
                Table.execute(getExpSchema(), sql, new Object[]{validator.getRowId(), validator.getRowId()});
                DbCache.remove(getTinfoValidator(), getCacheKey(property.getPropertyId()));
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    private void _removePropertyValidator(int propertyId, int validatorId)
    {
        try
        {
            if (propertyId != 0)
            {
                _removeValidatorReference(propertyId, validatorId);

                String sql = "DELETE FROM " + getTinfoValidator() +
                            " WHERE RowId = ?" +
                            " AND NOT EXISTS (SELECT * FROM " + getTinfoValidatorReference() + " VR " +
                                " WHERE  VR.ValidatorId = ?)";
                Table.execute(getExpSchema(), sql, new Object[]{validatorId, validatorId});
                DbCache.remove(getTinfoValidator(), getCacheKey(propertyId));
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public void savePropertyValidator(User user, DomainProperty property, IPropertyValidator validator) throws SQLException, ChangePropertyDescriptorException
    {
        if (property.getPropertyId() != 0)
        {
            try {
                addValidatorReference(property, validator.save(user, property.getContainer()));
                DbCache.remove(getTinfoValidator(), getCacheKey(property.getPropertyId()));
            }
            catch (ValidationException e)
            {
                throw new ChangePropertyDescriptorException(e.getMessage());
            }
        }
    }

    public void addValidatorReference(DomainProperty property, IPropertyValidator validator)
    {
        try
        {
            if (property.getPropertyId() != 0 && validator.getRowId() != 0)
            {
                String sql = "SELECT ValidatorId FROM " + getTinfoValidatorReference() + " WHERE ValidatorId=? AND PropertyId=?";
                Integer id = Table.executeSingleton(getExpSchema(), sql, new Object[]{validator.getRowId(), property.getPropertyId()}, Integer.class);
                if (id == null)
                {
                    SQLFragment insertSQL = new SQLFragment();
                    insertSQL.append("INSERT INTO ");
                    insertSQL.append(getTinfoValidatorReference());
                    insertSQL.append(" (ValidatorId,PropertyId) VALUES(?,?)");
                    insertSQL.add(validator.getRowId());
                    insertSQL.add(property.getPropertyId());

                    Table.execute(getExpSchema(), insertSQL);
                }
            }
            else
                throw new IllegalArgumentException("DomainProperty or IPropertyValidator row ID's cannot be null");
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    private void _removeValidatorReference(int propertyId, int validatorId)
    {
        try
        {
            if (propertyId != 0 && validatorId != 0)
            {
                String sql = "DELETE FROM " + getTinfoValidatorReference() + " WHERE ValidatorId=? AND PropertyId=?";
                Table.execute(getExpSchema(), sql, new Object[]{validatorId, propertyId});
            }
            else
                throw new IllegalArgumentException("DomainProperty or IPropertyValidator row ID's cannot be null");
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public void removeValidatorsForPropertyDescriptor(int descriptorId)
    {
        for (PropertyValidator pv : getValidators(descriptorId))
        {
            _removePropertyValidator(descriptorId, pv.getRowId());
        }
    }

    public void deleteAllValidatorsAndFormats(Container c) throws SQLException
    {
        String deletePropValidatorRefSql = "DELETE FROM " + getTinfoValidatorReference() +
                " WHERE ValidatorId IN (SELECT RowId FROM " + getTinfoValidator() + " WHERE Container = ?)";
        Table.execute(getExpSchema(), deletePropValidatorRefSql, new Object[]{c.getId()});

        String deletePropValidatorSql = "DELETE FROM " + getTinfoValidator() + " WHERE Container = ?";
        Table.execute(getExpSchema(), deletePropValidatorSql, new Object[]{c.getId()});

        String deleteConditionalFormatsSql = "DELETE FROM " + getTinfoConditionalFormat() + " WHERE PropertyId IN " +
                "(SELECT PropertyId FROM " + OntologyManager.getTinfoPropertyDescriptor() + " WHERE Container = ?)";
        Table.execute(getExpSchema(), deleteConditionalFormatsSql, new Object[]{c.getId()});

        DbCache.clear(getTinfoValidator());
        DbCache.clear(getTinfoConditionalFormat());
    }

    public void deleteConditionalFormats(int propertyId)
    {
        try
        {
            String deleteFormatSql = "DELETE FROM " + getTinfoConditionalFormat() + " WHERE PropertyId = ?";
            Table.execute(getExpSchema(), deleteFormatSql, new Object[]{propertyId});
            DbCache.remove(getTinfoValidator(), getCacheKey(propertyId));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void saveConditionalFormats(User user, PropertyDescriptor prop, List<ConditionalFormat> formats)
    {
        try
        {
            // Delete them all first
            deleteConditionalFormats(prop.getPropertyId());

            // Save the new ones
            int index = 0;
            for (ConditionalFormat format : formats)
            {
                // Table has two additional properties that aren't on the bean itself - propertyId and sortOrder
                Map<String, Object> row = new HashMap<String, Object>();
                row.put("Bold", format.isBold());
                row.put("Italic", format.isItalic());
                row.put("Strikethrough", format.isStrikethrough());
                row.put("TextColor", format.getTextColor());
                row.put("BackgroundColor", format.getBackgroundColor());
                row.put("Filter", format.getFilter());
                row.put("SortOrder", index++);
                row.put("PropertyId", prop.getPropertyId());

                Table.insert(user, getTinfoConditionalFormat(), row);
                DbCache.remove(getTinfoValidator(), getCacheKey(prop.getPropertyId()));
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}