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
package org.labkey.api.dataiterator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TestSchema;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.JunitUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.labkey.api.dataiterator.DataIteratorUtil.DUPLICATE_COLUMN_IN_DATA_ERROR;

/**
 * Passes rows through unchanged, reporting each input column that matches a column of the target table as a copy of
 * that target column.  This is required to be compatible with DatasetDataIteratorBuilder which calls setPropertyURI().
 * Matching uses {@link DataIteratorUtil#matchColumns}, which parallels the row-at-a-time insert path's
 * {@link org.labkey.api.data.ImportAliasable.Helper#createImportMap} (via {@code AbstractQueryUpdateService.normalizeColumnNames()});
 * the two disagree on import aliases, ties between target columns, and duplicate input columns.
 */
public class MatchMakerDataIterator extends WrapperDataIterator
{
    /** A target column, with its domain property and the input column holding its missing-value indicator */
    public static class TargetColumn
    {
        private final ColumnInfo _target;
        private final @Nullable DomainProperty _dp;
        private int _indexMv = SimpleTranslator.NO_MV_INDEX;

        private TargetColumn(ColumnInfo target, @Nullable DomainProperty dp)
        {
            _target = target;
            _dp = dp;
        }

        public ColumnInfo getTarget()
        {
            return _target;
        }

        public @Nullable DomainProperty getDp()
        {
            return _dp;
        }

        public int getIndexMv()
        {
            return _indexMv;
        }
    }

    private final Map<String, TargetColumn> _targetColumns;
    private final Map<FieldKey, TargetColumn> _unmatchedColumns;
    private final TargetColumn[] _matches;
    private final ColumnInfo[] _columns;

    /**
     * @param setupErrors collects matching errors, such as two input columns for one target column
     */
    public MatchMakerDataIterator(DataIterator input, TableInfo target, boolean useImportAliases, @Nullable Container c, ValidationException setupErrors)
    {
        super(input);

        Map<String, DomainProperty> propertiesMap = new HashMap<>();
        Domain d = target.getDomain();
        if (null != d)
        {
            for (DomainProperty dp : d.getProperties())
                propertiesMap.put(dp.getPropertyURI(), dp);
        }

        List<ColumnInfo> cols = target.getColumns();
        _targetColumns = new CaseInsensitiveHashMap<>(cols.size() * 4);
        _unmatchedColumns = new HashMap<>(cols.size() * 2);
        for (ColumnInfo col : cols)
        {
            if (col.isMvIndicatorColumn() || col.isRawValueColumn())
                continue;
            TargetColumn tc = new TargetColumn(col, propertiesMap.get(col.getPropertyURI()));
            if (null != col.getPropertyURI())
                _targetColumns.put(key(col), tc);
            _unmatchedColumns.put(col.getFieldKey(), tc);
        }

        Map<String, Integer> sourceColumnsMap = DataIteratorUtil.createColumnAndPropertyMap(input);
        List<ColumnInfo> matches = DataIteratorUtil.matchColumns(input, target, useImportAliases, setupErrors, c);
        _matches = new TargetColumn[input.getColumnCount() + 1];
        _columns = new ColumnInfo[input.getColumnCount() + 1];

        for (int i = 1; i <= input.getColumnCount(); i++)
        {
            ColumnInfo targetCol = matches.get(i);
            TargetColumn tc = null == targetCol ? null : _targetColumns.get(key(targetCol));
            if (null == tc)
                continue;

            ColumnInfo col = tc._target;
            if (!_unmatchedColumns.containsKey(col.getFieldKey()))
                setupErrors.addGlobalError(String.format(DUPLICATE_COLUMN_IN_DATA_ERROR, col.getName()));
            _unmatchedColumns.remove(col.getFieldKey());

            Integer indexMv = null == col.getMvColumnName() ? null : sourceColumnsMap.get(col.getMvColumnName().getName());
            tc._indexMv = null == indexMv ? SimpleTranslator.NO_MV_INDEX : indexMv;
            _matches[i] = tc;
            // Downstream builders mutate their input ColumnInfos, so don't expose the target's locked columns
            _columns[i] = new BaseColumnInfo(col);
        }
    }

    private static String key(ColumnInfo col)
    {
        return col.getPropertyURI() + ":" + col.getName().toLowerCase(Locale.ROOT);
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        ColumnInfo col = i < _columns.length ? _columns[i] : null;
        return null == col ? super.getColumnInfo(i) : col;
    }

    /** The target column input column {@code i} matched, if any */
    public @Nullable TargetColumn getMatch(int i)
    {
        return i < _matches.length ? _matches[i] : null;
    }

    /** The target column a column of this or a later iterator stands for, by property URI and name */
    public @Nullable TargetColumn getTargetColumn(ColumnInfo col)
    {
        return _targetColumns.get(key(col));
    }

    /** Target columns no input column matched */
    public @NotNull Collection<TargetColumn> getUnmatchedColumns()
    {
        return Collections.unmodifiableCollection(_unmatchedColumns.values());
    }

    public static class TestCase extends Assert
    {
        private static TableInfo target()
        {
            return TestSchema.getInstance().getTableInfoTestTable();
        }

        private static MatchMakerDataIterator match(ValidationException setupErrors, List<String> headers, List<String[]> rows)
        {
            return new MatchMakerDataIterator(new StringTestIterator(headers, rows), target(), false, JunitUtil.getTestContainer(), setupErrors);
        }

        private static void assertCopy(ColumnInfo target, ColumnInfo reported)
        {
            assertNotSame(target, reported);
            assertEquals(target.getName(), reported.getName());
            assertEquals(target.getPropertyURI(), reported.getPropertyURI());
            assertFalse(((BaseColumnInfo) reported).isLocked());
        }

        @Test
        public void reportsTargetColumnsForMatches() throws Exception
        {
            ColumnInfo intNotNull = target().getColumn("IntNotNull");
            ColumnInfo intNull = target().getColumn("IntNull");
            ColumnInfo text = target().getColumn("Text");
            ValidationException setupErrors = new ValidationException();

            // Matched by name, by label, by name in another case, and not at all
            List<String> headers = List.of("IntNotNull", intNull.getLabel(), "text", "Bogus");
            List<String[]> rows = List.<String[]>of(new String[]{"1", "2", "three", "four"});
            try (MatchMakerDataIterator it = match(setupErrors, headers, rows))
            {
                assertFalse(setupErrors.getMessage(), setupErrors.hasErrors());

                assertCopy(intNotNull, it.getColumnInfo(1));
                assertCopy(intNull, it.getColumnInfo(2));
                assertCopy(text, it.getColumnInfo(3));
                assertEquals("Bogus", it.getColumnInfo(4).getName());
                assertNull(it.getMatch(4));
                TargetColumn textMatch = it.getTargetColumn(it.getColumnInfo(3));
                assertNotNull(textMatch);
                assertSame(text, textMatch.getTarget());

                assertTrue(it.next());
                assertEquals(List.of("1", "2", "three", "four"), List.of(it.get(1), it.get(2), it.get(3), it.get(4)));
                assertFalse(it.next());

                List<ColumnInfo> unmatched = it.getUnmatchedColumns().stream().map(TargetColumn::getTarget).toList();
                assertFalse(unmatched.contains(intNotNull) || unmatched.contains(intNull) || unmatched.contains(text));
                assertTrue(unmatched.contains(target().getColumn("RowId")));
            }
        }

        @Test
        public void duplicateColumnIsASetupError() throws Exception
        {
            ValidationException setupErrors = new ValidationException();
            try (MatchMakerDataIterator ignored = match(setupErrors, List.of("Text", "text"), List.of()))
            {
                assertTrue(setupErrors.hasErrors());
            }
        }
    }
}
