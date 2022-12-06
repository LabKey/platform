/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.DefaultPropertyValidator;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.security.User;
import org.labkey.experiment.api.ExpMaterialTableImpl;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * User: jeckels
 * Date: Jan 26, 2010
 */
public class LookupValidator extends DefaultPropertyValidator implements ValidatorKind
{
    @Override
    public String getName()
    {
        return "Lookup Property Validator";
    }

    @Override
    public IPropertyValidator createInstance()
    {
        PropertyValidatorImpl validator = new PropertyValidatorImpl(new PropertyValidator());
        validator.setTypeURI(getTypeURI());

        return validator;
    }

    @Override
    public String getTypeURI()
    {
        return createValidatorURI(PropertyValidatorType.Lookup).toString();
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public boolean isValid(IPropertyValidator validator, List<ValidationError> errors)
    {
        return true;
    }

    private static class LookupKey
    {
        private final String _schema;
        private final String _query;
        private final String _container;
        private final JdbcType _type;

        public LookupKey(PropertyDescriptor field)
        {
            _schema = field.getLookupSchema();
            _query = field.getLookupQuery();
            _container = field.getLookupContainer();
            _type = field.getJdbcType();
        }

        public LookupKey(ForeignKey fk, JdbcType jdbcType)
        {
            _schema = fk.getLookupSchemaName();
            _query = fk.getLookupTableName();
            _container = (null == fk.getLookupContainer() ? null : fk.getLookupContainer().getId());
            _type = jdbcType;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LookupKey that = (LookupKey) o;

            if (_container != null ? !_container.equals(that._container) : that._container != null) return false;
            if (_query != null ? !_query.equals(that._query) : that._query != null) return false;
            if (_type != null ? !_type.equals(that._type) : that._type != null) return false;
            return !(_schema != null ? !_schema.equals(that._schema) : that._schema != null);
        }

        @Override
        public int hashCode()
        {
            int result = _schema != null ? _schema.hashCode() : 0;
            result = 31 * result + (_query != null ? _query.hashCode() : 0);
            result = 31 * result + (_container != null ? _container.hashCode() : 0);
            result = 31 * result + (_type != null ? _type.hashCode() : 0);
            return result;
        }
    }

    private static class LookupValues extends HashSet<Object>
    {
        final private Container _container;

        public LookupValues(ColumnInfo field, Container defaultContainer, List<ValidationError> errors)
        {
            if (field.getFk().getLookupContainer() != null)
            {
                _container = field.getFk().getLookupContainer();
            }
            else
            {
                _container = defaultContainer;
            }

            processTableInfo(field.getFk().getLookupTableInfo(), field.getJdbcType(), field.getFk().getLookupTableName(), field.getNonBlankCaption(), errors);
        }

        public LookupValues(PropertyDescriptor field, Container defaultContainer, User user, List<ValidationError> errors)
        {
            if (field.getLookupContainer() != null)
            {
                _container = ContainerManager.getForId(field.getLookupContainer());
            }
            else
            {
                _container = defaultContainer;
            }

            if (user == null)
            {
                throw new IllegalArgumentException("Must supply a user");
            }

            if (_container == null)
            {
                errors.add(new SimpleValidationError("Could not find the lookup's target folder for field '" + field.getNonBlankCaption() + "'"));
            }
            else
            {
                QuerySchema userSchema = QueryService.get().getUserSchema(user, _container, field.getLookupSchema());
                if (userSchema == null)
                {
                    errors.add(new SimpleValidationError("Could not find the lookup's target schema ('" + field.getLookupSchema() + "') for field '" + field.getNonBlankCaption() + "'"));
                }
                else
                {
                    processTableInfo(userSchema.getTableForInsert(field.getLookupQuery()), field.getJdbcType(), field.getLookupQuery(), field.getNonBlankCaption(), errors);
                }
            }
        }

        private void processTableInfo(TableInfo tableInfo, JdbcType jdbcType, String queryName, String label, List<ValidationError> errors)
        {
            if (tableInfo == null)
            {
                errors.add(new SimpleValidationError("Could not find the lookup's target query ('" + queryName + "') for field '" + label + "'"));
            }
            else
            {
                List<ColumnInfo> keyCols = tableInfo.getPkColumns();

                if (keyCols.size() != 1)
                {
                    errors.add(new SimpleValidationError("Could not validate target query ('" + queryName + "') because it has " + keyCols.size() + " columns instead of one for the field '" + label + "'"));
                }
                else
                {
                    ColumnInfo lookupTargetCol = keyCols.get(0);
                    // Hack for sample types - see also revision 37612
                    if (lookupTargetCol.getJdbcType() != jdbcType && jdbcType.isText() && tableInfo instanceof ExpMaterialTableImpl)
                    {
                        ColumnInfo nameCol = tableInfo.getColumn(ExpMaterialTableImpl.Column.Name.toString());
                        assert nameCol != null : "Could not find Name column in SampleType table";
                        if (nameCol != null)
                        {
                            lookupTargetCol = nameCol;
                        }
                    }
                    Collection<?> keys = new TableSelector(lookupTargetCol).getCollection(lookupTargetCol.getJavaObjectClass());
                    addAll(keys);
                }
            }
        }

        public Container getContainer()
        {
            return _container;
        }
    }

    @Override
    public boolean validate(IPropertyValidator validator,
                            ColumnRenderProperties crpField,
                            @NotNull Object value,
                            List<ValidationError> errors,
                            ValidatorContext validatorCache)
    {
        //noinspection ConstantConditions
        assert value != null : "Shouldn't be validating a null value";

        if (value != null)
            value = ConvertHelper.convert(value, crpField.getJavaObjectClass());

        if (crpField instanceof PropertyDescriptor field)
        {
            if (field.getLookupQuery() != null && field.getLookupSchema() != null)
            {
                LookupKey key = new LookupKey(field);

                LookupValues validValues = (LookupValues) validatorCache.get(LookupValidator.class, key);
                if (validValues == null)
                {
                    validValues = new LookupValues(field, validatorCache.getContainer(), validatorCache.getUser(), errors);
                }
                return isLookupValid(value, errors, validatorCache, key, field.getLookupSchema(),
                        field.getLookupQuery(), field.getNonBlankCaption(), validValues);
            }
        }
        else if (crpField instanceof ColumnInfo field)
        {
            if (field.getFk() != null)
            {
                LookupKey key = new LookupKey(field.getFk(), field.getJdbcType());
                LookupValues validValues = (LookupValues) validatorCache.get(LookupValidator.class, key);

                if (validValues == null)
                {
                    validValues = new LookupValues(field, validatorCache.getContainer(), errors);
                }
                return isLookupValid(value, errors, validatorCache, key, field.getFk().getLookupSchemaName(),
                        field.getFk().getLookupTableName(), field.getNonBlankCaption(), validValues);
            }
        }
        else
        {
            throw new IllegalArgumentException("Unknown column type : '" + crpField.getClass() + "'");
        }

        return true;
    }

    private boolean isLookupValid(@NotNull Object value,
                                  List<ValidationError> errors,
                                  ValidatorContext validatorCache,
                                  LookupKey key,
                                  String schemaName,
                                  String queryName,
                                  String label,
                                  LookupValues validValues)
    {
        validatorCache.put(LookupValidator.class, key, validValues);

        if (validValues.contains(value))
        {
            return true;
        }

        errors.add(new PropertyValidationError("Value '" + value + "' was not present in lookup target '" +
                schemaName + "." + queryName + "' for field '" + label + "'",label));

        return false;
    }
}
