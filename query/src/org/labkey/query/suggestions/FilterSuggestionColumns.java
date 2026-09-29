/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.query.suggestions;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.compliance.PhiTransformedColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.IMultiValuedDisplayColumn;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.PHI;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.suggestions.SuggestionColumn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Describes the candidate columns of a grid search from their metadata alone. Mirrors planColumns/classifyColumn in
 * the @labkey/components ClientFilterSuggestionEngine so both pick the same columns and filter fieldKeys.
 */
public class FilterSuggestionColumns
{
    private enum Strategy { BOOLEAN, DATE, FIXED, FLOAT, INT, KEY, LOOKUP, STRING }

    private record Candidate(ColumnInfo column, @Nullable String caption) {}

    /** @param lookups the lookup column whose target table supplies each lookup-valued column's values */
    public record Described(@NotNull List<SuggestionColumn> columns, @NotNull Map<SuggestionColumn, ColumnInfo> lookups) {}

    /**
     * @param fieldKeys the grid's display columns; only those that resolve and that the user may read are kept, plus
     *                  integer primary keys, which Q matches even when they are not displayed
     */
    public static @NotNull Described describe(@NotNull TableInfo table, @Nullable CustomView view, @Nullable Collection<String> fieldKeys)
    {
        Set<FieldKey> requested = new LinkedHashSet<>();
        if (fieldKeys != null)
        {
            for (String fieldKey : fieldKeys)
                if (StringUtils.isNotBlank(fieldKey))
                    requested.add(FieldKey.fromString(fieldKey));
        }

        // Lookup ancestors are resolved too so their PHI level can be checked
        Set<FieldKey> toResolve = new LinkedHashSet<>();
        for (FieldKey fieldKey : requested)
        {
            for (FieldKey key = fieldKey; key != null; key = key.getParent())
                toResolve.add(key);
        }
        Map<FieldKey, ColumnInfo> resolved = toResolve.isEmpty() ? Map.of() : QueryService.get().getColumns(table, toResolve);
        PHI maxAllowedPhi = table.getUserMaxAllowedPhiLevel();
        Map<FieldKey, String> viewCaptions = getViewCaptions(view);

        List<Candidate> candidates = new ArrayList<>();
        for (FieldKey fieldKey : requested)
        {
            ColumnInfo column = resolved.get(fieldKey);
            if (column != null && isReadable(fieldKey, resolved, maxAllowedPhi))
                candidates.add(new Candidate(column, viewCaptions.get(fieldKey)));
        }

        Set<FieldKey> pkFieldKeys = new HashSet<>();
        for (ColumnInfo pk : table.getPkColumns())
        {
            pkFieldKeys.add(pk.getFieldKey());
            if (pk.getJdbcType().isInteger() && isAllowed(pk, maxAllowedPhi)
                    && candidates.stream().noneMatch(c -> c.column().getFieldKey().equals(pk.getFieldKey())))
                candidates.add(new Candidate(pk, viewCaptions.get(pk.getFieldKey())));
        }

        String titleColumn = table.getTitleColumn();
        Set<String> seen = new HashSet<>();
        List<SuggestionColumn> columns = new ArrayList<>();
        Map<SuggestionColumn, ColumnInfo> lookups = new IdentityHashMap<>();
        for (Candidate candidate : candidates)
        {
            SuggestionColumn column = toSuggestionColumn(candidate, pkFieldKeys.contains(candidate.column().getFieldKey()), titleColumn);
            if (column != null && seen.add(column.getFieldKey().toLowerCase(Locale.ROOT)))
            {
                columns.add(column);
                if (column.getType() == SuggestionColumn.Type.STRING && column.getValues() == null && isLookup(candidate.column()))
                    lookups.put(column, candidate.column());
            }
        }
        return new Described(columns, lookups);
    }

    private static Map<FieldKey, String> getViewCaptions(@Nullable CustomView view)
    {
        Map<FieldKey, String> captions = new HashMap<>();
        if (view != null)
        {
            for (Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>> entry : view.getColumnProperties())
            {
                String title = entry.getValue().get(CustomViewInfo.ColumnProperty.columnTitle);
                if (StringUtils.isNotBlank(title))
                    captions.put(entry.getKey(), title);
            }
        }
        return captions;
    }

    static boolean isAllowed(@NotNull ColumnInfo column, @NotNull PHI maxAllowedPhi)
    {
        return !(column instanceof PhiTransformedColumnInfo) && column.getPHI().isLevelAllowed(maxAllowedPhi);
    }

    private static boolean isReadable(FieldKey fieldKey, Map<FieldKey, ColumnInfo> resolved, PHI maxAllowedPhi)
    {
        for (FieldKey key = fieldKey; key != null; key = key.getParent())
        {
            ColumnInfo column = resolved.get(key);
            if (column == null || !isAllowed(column, maxAllowedPhi))
                return false;
        }
        return true;
    }

    private static boolean isLookup(ColumnInfo column)
    {
        ForeignKey fk = column.getFk();
        return fk != null && column.getFkTableDescription() != null
            && !(fk instanceof RowIdForeignKey rowIdFk && rowIdFk.getOriginalColumn().equals(column));
    }

    private static boolean isFacetingOff(ColumnInfo column, DisplayColumn renderer)
    {
        if (PropertyType.FILE_LINK == column.getPropertyType() || renderer instanceof IMultiValuedDisplayColumn)
            return true;
        TableInfo parent = column.getParentTable();
        if (parent != null && parent.getSqlDialect() != null && !parent.getSqlDialect().isSortableDataType(column.getSqlTypeName()))
            return true;
        return column.getFacetingBehaviorType() == FacetingBehaviorType.ALWAYS_OFF;
    }

    private static @Nullable List<String> getValidValues(ColumnInfo column)
    {
        IPropertyValidator validator = PropertyService.get().getValidatorForColumn(column, PropertyValidatorType.TextChoice);
        return validator == null ? null : PropertyService.get().getTextChoiceValidatorOptions(validator);
    }

    private static @Nullable Strategy classify(ColumnInfo column, DisplayColumn renderer, @Nullable ColumnInfo displayField, @Nullable List<String> validValues, boolean isPrimaryKey)
    {
        if (!renderer.isFilterable() || isFacetingOff(column, renderer))
            return null;
        String inputType = column.getInputType();
        if ("file".equals(inputType) || "textarea".equals(inputType) || PropertyType.TIME.getTypeUri().equals(column.getRangeURI()))
            return null;
        if (renderer instanceof IMultiValuedDisplayColumn || PropertyType.MULTI_CHOICE.getTypeUri().equals(column.getRangeURI()))
            return null;
        if (column.getFk() instanceof MultiValuedForeignKey mvfk && mvfk.getJunctionLookup() != null && isLookup(column))
            return null;

        String jsonType = displayField != null ? displayField.getRenderer().getJsonTypeName() : renderer.getJsonTypeName();
        if (isPrimaryKey && "int".equals(jsonType))
            return Strategy.KEY;
        if (validValues != null && !validValues.isEmpty())
            return Strategy.FIXED;
        return switch (jsonType)
        {
            case "boolean" -> Strategy.BOOLEAN;
            case "string" -> isLookup(column) ? Strategy.LOOKUP : Strategy.STRING;
            case "int" -> Strategy.INT;
            case "float", "double" -> Strategy.FLOAT;
            case "date" -> Strategy.DATE;
            default -> null;
        };
    }

    private static @Nullable SuggestionColumn toSuggestionColumn(Candidate candidate, boolean isPrimaryKey, @Nullable String titleColumn)
    {
        ColumnInfo column = candidate.column();
        DisplayColumn renderer = column.getRenderer();
        ColumnInfo displayField = renderer.getDisplayColumnInfo();
        if (displayField == column)
            displayField = null;

        List<String> validValues = getValidValues(column);
        Strategy strategy = classify(column, renderer, displayField, validValues, isPrimaryKey);
        if (strategy == null)
            return null;

        // Filters on a lookup go on its display column, as the grid's own column filters do
        ColumnInfo filterColumn = displayField != null && isLookup(column) ? displayField : column;
        String caption = candidate.caption() != null ? candidate.caption() : renderer.getCaption(null, false);
        SuggestionColumn.Type type = switch (strategy)
        {
            case BOOLEAN -> SuggestionColumn.Type.BOOLEAN;
            case DATE -> SuggestionColumn.Type.DATE;
            case FLOAT -> SuggestionColumn.Type.FLOAT;
            case INT, KEY -> SuggestionColumn.Type.INT;
            case FIXED, LOOKUP, STRING -> SuggestionColumn.Type.STRING;
        };

        SuggestionColumn suggestionColumn = new SuggestionColumn(filterColumn.getFieldKey().toString(), caption, type, filterColumn);
        suggestionColumn.setTitle(titleColumn != null && titleColumn.equalsIgnoreCase(column.getFieldKey().toString()));
        suggestionColumn.setUniqueId(column.isUniqueIdField());
        suggestionColumn.addNameExpression(column.getNameExpression());

        switch (strategy)
        {
            case BOOLEAN -> suggestionColumn.setValues(List.of("true", "false"));
            case FIXED -> suggestionColumn.setValues(List.copyOf(Objects.requireNonNull(validValues)));
            case KEY -> suggestionColumn.setKey(true);
            // ColumnFactsCache replaces this with a range when it can read the column's data
            case DATE, FLOAT, INT -> suggestionColumn.setRangeUnknown(true);
            default -> {}
        }
        return suggestionColumn;
    }
}
