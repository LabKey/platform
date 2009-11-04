/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.query.ValidationException;

import java.sql.SQLException;

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

    public PropertyValidator[] getValidators(DomainProperty property)
    {
        return _getValidators(property.getPropertyId());
    }

    public PropertyValidator[] getValidators(PropertyDescriptor property)
    {
        return _getValidators(property.getPropertyId());
    }

    private String getCacheKey(int propertyId)
    {
        return String.valueOf(propertyId);
    }

    private PropertyValidator[] _getValidators(int propertyId)
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
        for (PropertyValidator pv : _getValidators(descriptorId))
        {
            _removePropertyValidator(descriptorId, pv.getRowId());
        }
    }

    public void deleteAllValidators(Container c) throws SQLException
    {
        String deletePropValidatorRefSql = "DELETE FROM " + getTinfoValidatorReference() +
                " WHERE ValidatorId IN (SELECT RowId FROM " + getTinfoValidator() + " WHERE Container = ?)";
        Table.execute(getExpSchema(), deletePropValidatorRefSql, new Object[]{c.getId()});

        String deletePropValidatorSql = "DELETE FROM " + getTinfoValidator() + " WHERE Container = ?";
        Table.execute(getExpSchema(), deletePropValidatorSql, new Object[]{c.getId()});

        DbCache.clear(getTinfoValidator());
    }
}