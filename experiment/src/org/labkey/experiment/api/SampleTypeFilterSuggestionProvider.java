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
package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.suggestions.FilterSuggestionContext;
import org.labkey.api.query.suggestions.FilterSuggestionProvider;
import org.labkey.api.query.suggestions.SuggestionColumn;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Adds a sample type's name expressions to its Name column and keys its cached facts to its sample data changes. */
public class SampleTypeFilterSuggestionProvider implements FilterSuggestionProvider
{
    @Override
    public boolean handles(@NotNull TableInfo table)
    {
        return getSampleType(table) != null;
    }

    @Override
    public void describe(@NotNull FilterSuggestionContext context, @NotNull List<SuggestionColumn> columns)
    {
        ExpSampleType sampleType = getSampleType(context.getTable());
        if (sampleType == null)
            return;

        columns.stream()
            .filter(column -> ExpMaterialTable.Column.Name.name().equalsIgnoreCase(column.getFieldKey()))
            .forEach(column -> {
                column.addNameExpression(sampleType.getNameExpression());
                // Null when the default applies; default aliquot names extend the parent's name, which the name expression already matches
                column.addNameExpression(sampleType.getAliquotNameExpression());
            });
    }

    @Override
    public @Nullable String getChangeToken(@NotNull TableInfo table)
    {
        ExpSampleType sampleType = getSampleType(table);
        return sampleType == null ? null : SampleTypeService.get().getDataChangeToken(sampleType);
    }

    private static @Nullable ExpSampleType getSampleType(@NotNull TableInfo table)
    {
        return table instanceof ExpMaterialTableImpl materials ? materials.getSampleType() : null;
    }

    public static class TestCase extends Assert
    {
        private static final String ROW_ID = ExpMaterialTable.Column.RowId.name();

        private static User _user;
        private static Container _c;

        @BeforeClass
        public static void setup()
        {
            JunitUtil.deleteTestContainer();

            _c = JunitUtil.getTestContainer();
            _user = TestContext.get().getUser();
        }

        @AfterClass
        public static void tearDown()
        {
            JunitUtil.deleteTestContainer();
        }

        @Test
        public void testDataChangeToken() throws Exception
        {
            ExpSampleType st = createSampleType("SuggestToken", null, null);
            Set<String> seen = new HashSet<>();
            assertNewToken(seen, st, "initial");

            BatchValidationException errors = new BatchValidationException();
            List<Map<String, Object>> inserted = getUpdateService(st).insertRows(_user, _c, List.of(CaseInsensitiveHashMap.of("name", "S-1", "prop", "a")), errors, null, null);
            if (errors.hasErrors())
                throw errors;
            assertNewToken(seen, st, "insert");
            Object rowId = Objects.requireNonNull(inserted).getFirst().get(ROW_ID);

            getUpdateService(st).updateRows(_user, _c, List.of(CaseInsensitiveHashMap.of(ROW_ID, rowId, "prop", "b")), null, errors, null, null);
            if (errors.hasErrors())
                throw errors;
            assertNewToken(seen, st, "update");

            // Updates without a watermark and schema changes drop the materialized view without bumping the data counters
            SampleTypeServiceImpl.get().refreshSampleTypeMaterializedView(st, SampleTypeServiceImpl.SampleChangeType.update);
            assertNewToken(seen, st, "update without watermark");
            SampleTypeServiceImpl.get().refreshSampleTypeMaterializedView(st, SampleTypeServiceImpl.SampleChangeType.schema);
            assertNewToken(seen, st, "schema change");

            getUpdateService(st).deleteRows(_user, _c, List.of(CaseInsensitiveHashMap.of(ROW_ID, rowId)), null, null);
            assertNewToken(seen, st, "delete");

            // Simulates the cache-clear listener; the replacement counters restart from the clock
            ExpMaterialTableImpl._materializedQueries.remove(st.getLSID());
            ExpMaterialTableImpl._invalidationCounters.remove(st.getLSID());
            assertNewToken(seen, st, "reset");
        }

        @Test
        public void testDescribe() throws Exception
        {
            ExpSampleType st = createSampleType("SuggestNames", "PL-${genId}", "PLA-${genId}");
            ExpSampleType plain = createSampleType("SuggestPlain", null, null);
            SampleTypeFilterSuggestionProvider provider = new SampleTypeFilterSuggestionProvider();

            TableInfo table = getSamplesTable(st);
            assertTrue(provider.handles(table));
            assertFalse(provider.handles(Objects.requireNonNull(QueryService.get().getUserSchema(_user, _c, ExpSchema.SCHEMA_NAME).getTable(ExpSchema.TableType.Materials.name()))));

            SuggestionColumn name = nameColumn();
            SuggestionColumn prop = new SuggestionColumn("prop", "Prop", SuggestionColumn.Type.STRING, null);
            provider.describe(context(table), List.of(name, prop));
            assertEquals(List.of("PL-${genId}", "PLA-${genId}"), name.getNameExpressions());
            assertTrue(prop.getNameExpressions().isEmpty());
            assertEquals(SampleTypeService.get().getDataChangeToken(st), provider.getChangeToken(table));

            SuggestionColumn plainName = nameColumn();
            provider.describe(context(getSamplesTable(plain)), List.of(plainName));
            assertTrue(plainName.getNameExpressions().isEmpty());
        }

        private static void assertNewToken(Set<String> seen, ExpSampleType st, String change)
        {
            String token = SampleTypeService.get().getDataChangeToken(st);
            assertTrue("Token repeated after " + change + ": " + token, seen.add(token));
        }

        private static SuggestionColumn nameColumn()
        {
            return new SuggestionColumn("Name", "Sample ID", SuggestionColumn.Type.STRING, null);
        }

        private static ExpSampleType createSampleType(String name, @Nullable String nameExpression, @Nullable String aliquotNameExpression) throws Exception
        {
            List<GWTPropertyDescriptor> props = List.of(new GWTPropertyDescriptor("name", "string"), new GWTPropertyDescriptor("prop", "string"));
            return SampleTypeService.get().createSampleType(_c, _user, name, null, props, List.of(), -1, -1, -1, -1, nameExpression, aliquotNameExpression, null, null, null, null);
        }

        private static TableInfo getSamplesTable(ExpSampleType st)
        {
            return Objects.requireNonNull(QueryService.get().getUserSchema(_user, _c, SamplesSchema.SCHEMA_NAME).getTable(st.getName()));
        }

        private static QueryUpdateService getUpdateService(ExpSampleType st)
        {
            return Objects.requireNonNull(getSamplesTable(st).getUpdateService());
        }

        private static FilterSuggestionContext context(TableInfo table)
        {
            return new FilterSuggestionContext()
            {
                @Override
                public @NotNull TableInfo getTable()
                {
                    return table;
                }

                @Override
                public @NotNull ContainerFilter getContainerFilter()
                {
                    return ContainerFilter.current(_c, _user);
                }

                @Override
                public @NotNull Container getContainer()
                {
                    return _c;
                }

                @Override
                public @NotNull User getUser()
                {
                    return _user;
                }
            };
        }
    }
}
