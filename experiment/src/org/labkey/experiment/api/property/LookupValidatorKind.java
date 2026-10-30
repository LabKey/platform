/*
 * Copyright (c) 2010-2026 LabKey Corporation
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
import org.apache.commons.collections4.ListUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BatchSize;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.validator.PrefetchingValidator;
import org.labkey.api.data.validator.PropertyValidator;
import org.labkey.api.dataiterator.SimpleTranslator.RemapConverter;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.DefaultValidatorKind;
import org.labkey.api.exp.property.PropertyValidatorDef;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.security.User;
import org.labkey.experiment.api.ExpMaterialTableImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Requires a value to exist as the lookup target's key, or as a sample's Name for a text lookup to a sample type.
 * Remembers which values exist per {@link ValidatorContext}, querying a batch at a time instead of loading the target.
 */
public class LookupValidatorKind extends DefaultValidatorKind implements ValidatorKind
{
    @Override
    public String getName()
    {
        return "Lookup Property Validator";
    }

    @Override
    public PropertyValidatorDef createInstance()
    {
        PropertyValidatorDefImpl validator = new PropertyValidatorDefImpl(new PropertyValidatorBean());
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
    public boolean isValid(PropertyValidatorDef validator, List<ValidationError> errors)
    {
        return true;
    }

    @Override
    public PropertyValidator bind(PropertyValidatorDef validator, ColumnRenderProperties field)
    {
        return new BoundLookupValidator(field, validator, this);
    }

    /** Lets ValidatorIterator prefetch a window of values; the lookup's answers live in the ValidatorContext, not here */
    private static class BoundLookupValidator extends PropertyValidator implements PrefetchingValidator
    {
        private final ColumnRenderProperties _field;
        private final LookupValidatorKind _kind;

        BoundLookupValidator(ColumnRenderProperties field, PropertyValidatorDef validator, LookupValidatorKind kind)
        {
            super(field, validator, kind);
            _field = field;
            _kind = kind;
        }

        @Override
        public String validate(int rowNum, Object value, ValidatorContext validatorContext, @Nullable Object providedValue)
        {
            return super.validate(rowNum, value, validatorContext, providedValue);
        }

        @Override
        public void prefetch(Collection<?> values, ValidatorContext validatorContext)
        {
            LookupValues validValues = _kind.getLookupValues(_field, validatorContext);
            if (validValues == null)
                return;

            List<Object> converted = new ArrayList<>(values.size());
            for (Object value : values)
            {
                try
                {
                    converted.add(_field.convert(value));
                }
                catch (ConversionException e)
                {
                    // validate() reports it
                }
            }
            validValues.prefetch(converted);
        }
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

    /** Remembers which values exist in a lookup target, querying a batch at a time instead of loading the whole target */
    private static class LookupValues
    {
        private final String _schemaName;
        private final String _queryName;
        private final List<ValidationError> _setupErrors = new ArrayList<>();
        private ColumnInfo _keyCol;
        private boolean _caseInsensitive;
        // GH Issue 1561: bounded, so a large lookup target is never held in memory
        private final Map<Object, Boolean> _found = RemapConverter.lruMap();

        public LookupValues(ColumnInfo field)
        {
            _schemaName = field.getFk().getLookupSchemaName();
            _queryName = field.getFk().getLookupTableName();
            processTableInfo(field.getFk().getLookupTableInfo(), field.getJdbcType(), field.getNonBlankCaption());
        }

        public LookupValues(PropertyDescriptor field, Container defaultContainer, User user)
        {
            _schemaName = field.getLookupSchema();
            _queryName = field.getLookupQuery();

            Container container = field.getLookupContainer() != null ? ContainerManager.getForId(field.getLookupContainer()) : defaultContainer;

            if (user == null)
            {
                throw new IllegalArgumentException("Must supply a user");
            }

            if (container == null)
            {
                _setupErrors.add(new SimpleValidationError("Could not find the lookup's target folder for field '" + field.getNonBlankCaption() + "'"));
            }
            else
            {
                QuerySchema userSchema = QueryService.get().getUserSchema(user, container, _schemaName);
                if (userSchema == null)
                {
                    _setupErrors.add(new SimpleValidationError("Could not find the lookup's target schema ('" + _schemaName + "') for field '" + field.getNonBlankCaption() + "'"));
                }
                else
                {
                    processTableInfo(userSchema.getTableForInsert(_queryName), field.getJdbcType(), field.getNonBlankCaption());
                }
            }
        }

        private void processTableInfo(TableInfo tableInfo, JdbcType jdbcType, String label)
        {
            if (tableInfo == null)
            {
                _setupErrors.add(new SimpleValidationError("Could not find the lookup's target query ('" + _queryName + "') for field '" + label + "'"));
            }
            else
            {
                List<ColumnInfo> keyCols = tableInfo.getPkColumns();

                if (keyCols.size() != 1)
                {
                    _setupErrors.add(new SimpleValidationError("Could not validate target query ('" + _queryName + "') because it has " + keyCols.size() + " columns instead of one for the field '" + label + "'"));
                }
                else
                {
                    ColumnInfo lookupTargetCol = keyCols.getFirst();
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
                    _keyCol = lookupTargetCol;
                    _caseInsensitive = !tableInfo.getSqlDialect().isCaseSensitive();
                }
            }
        }

        public List<ValidationError> getSetupErrors()
        {
            return _setupErrors;
        }

        public void prefetch(Collection<?> values)
        {
            if (_keyCol == null)
                return;

            List<Object> uncached = values.stream()
                .map(this::normalize)
                .filter(k -> k != null && !_found.containsKey(k))
                .distinct()
                .toList();

            for (List<Object> batch : ListUtils.partition(uncached, BatchSize.get(BatchSize.Kind.ROWS)))
            {
                Set<Object> existing = new HashSet<>();
                new TableSelector(_keyCol, new SimpleFilter(_keyCol.getFieldKey(), batch, CompareType.IN), null)
                    .forEach(_keyCol.getJavaObjectClass(), v -> existing.add(normalize(v)));
                for (Object k : batch)
                    _found.put(k, existing.contains(k));
            }
        }

        public boolean contains(Object value)
        {
            if (_keyCol == null)
                return false;

            Object k = normalize(value);
            if (k == null)
                return false;

            if (!_found.containsKey(k))
                prefetch(List.of(value));
            return Boolean.TRUE.equals(_found.get(k));
        }

        // Matches the key's type and the database's case sensitivity, so a cached answer agrees with what a query returns
        private @Nullable Object normalize(@Nullable Object value)
        {
            try
            {
                Object k = _keyCol.getJdbcType().convert(value);
                return _caseInsensitive && k instanceof String str ? str.toLowerCase(Locale.ROOT) : k;
            }
            catch (ConversionException e)
            {
                return null;
            }
        }
    }

    @Override
    public boolean validate(PropertyValidatorDef validator,
                            ColumnRenderProperties crpField,
                            @NotNull Object value,
                            List<ValidationError> errors,
                            ValidatorContext validatorCache,
                            @Nullable Object providedValue)
    {
        //noinspection ConstantConditions
        assert value != null : "Shouldn't be validating a null value";

        LookupValues validValues = getLookupValues(crpField, validatorCache);
        if (validValues == null)
            return true;

        if (!validValues.getSetupErrors().isEmpty())
        {
            errors.addAll(validValues.getSetupErrors());
            return false;
        }

        value = crpField.convert(value);
        // Queries this one value unless ValidatorIterator already prefetched its window
        if (validValues.contains(value))
            return true;

        String label = crpField.getNonBlankCaption();
        errors.add(new PropertyValidationError("Value '" + value + "' was not present in lookup target '" +
                validValues._schemaName + "." + validValues._queryName + "' for field '" + label + "'", label));

        return false;
    }

    private @Nullable LookupValues getLookupValues(ColumnRenderProperties crpField, ValidatorContext validatorCache)
    {
        LookupKey key;
        Supplier<LookupValues> factory;
        if (crpField instanceof PropertyDescriptor field)
        {
            if (field.getLookupQuery() == null || field.getLookupSchema() == null)
                return null;
            key = new LookupKey(field);
            factory = () -> new LookupValues(field, validatorCache.getContainer(), validatorCache.getUser());
        }
        else if (crpField instanceof ColumnInfo field)
        {
            if (field.getFk() == null)
                return null;
            key = new LookupKey(field.getFk(), field.getJdbcType());
            factory = () -> new LookupValues(field);
        }
        else
        {
            throw new IllegalArgumentException("Unknown column type : '" + crpField.getClass() + "'");
        }

        LookupValues validValues = (LookupValues) validatorCache.get(LookupValidatorKind.class, key);
        if (validValues == null)
        {
            validValues = factory.get();
            validatorCache.put(LookupValidatorKind.class, key, validValues);
        }
        return validValues;
    }
}
