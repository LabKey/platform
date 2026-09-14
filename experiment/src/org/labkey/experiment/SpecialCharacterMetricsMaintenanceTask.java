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
package org.labkey.experiment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeDomainKind;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.api.util.TestContext;
import org.labkey.experiment.api.DataClassDomainKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SpecialCharacterMetricsMaintenanceTask implements MaintenanceTask
{
    public static final String NAME = "SpecialCharacterMetricsMaintenanceTask";

    private static final String TEXT_CHOICE_TYPE_URI = "urn:lsid:labkey.com:PropertyValidator:textchoice";
    private static final String TYPE_TEXT_CHOICE = "TextChoice";
    private static final String TYPE_MVTC = "MVTC";
    private static final String TYPE_TEXT = "Text";
    private static final String TYPE_MULTILINE = "MultiLine";
    private static final String TYPE_DATA_NAME = "dataName";
    private static final String TYPE_OBJECT_STRING_VALUE = "objectStringValue";

    // Audit provisioned tables are excluded from the Text/Multiline scan.
    private static final String AUDIT_SCHEMA_NAME = "audit";

    private static final String[] CHAR_KEYS = {"semicolon", "comma", "newline", "doubleQuote"};

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Task to calculate metrics for special characters in text-bearing fields";
    }

    @Override
    public void run(Logger log)
    {
        try
        {
            DbScope scope = ExperimentService.get().getSchema().getScope();
            SqlDialect dialect = scope.getSqlDialect();

            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("Run time", new Date());

            Map<String, Map<String, Long>> counts = new LinkedHashMap<>();
            for (String type : new String[]{TYPE_TEXT_CHOICE, TYPE_MVTC, TYPE_TEXT, TYPE_MULTILINE, TYPE_DATA_NAME, TYPE_OBJECT_STRING_VALUE})
            {
                Map<String, Long> inner = new LinkedHashMap<>();
                for (String ck : CHAR_KEYS)
                    inner.put(ck, 0L);
                counts.put(type, inner);
            }
            collectChoiceFieldMetrics(scope, counts);
            collectTextFieldMetrics(scope, dialect, counts, log);
            collectObjectPropertyMetrics(scope, counts);

            metric.putAll(counts);

            SpecialCharacterMetricsProvider.getInstance().updateMetrics(Map.of(SpecialCharacterMetricsProvider.METRIC_KEY, metric));

        }
        catch (Exception e)
        {
            log.error("Unable to run special character metrics task.", e);
        }
    }

    private void collectChoiceFieldMetrics(DbScope scope, Map<String, Map<String, Long>> counts)
    {
        SQLFragment sql = new SQLFragment("SELECT pd.rangeuri AS rangeuri");
        for (String ck : CHAR_KEYS)
        {
            sql.append(", SUM(CASE WHEN (");
            appendBoolExpr(sql, ck, new SQLFragment("pv.expression"));
            sql.append(") THEN 1 ELSE 0 END) AS ").append(ck.toLowerCase());
        }
        sql.append(" FROM exp.propertyvalidator pv");
        sql.append(" JOIN exp.propertydescriptor pd ON pv.propertyid = pd.propertyid");
        sql.append(" WHERE pv.typeuri = ?").add(TEXT_CHOICE_TYPE_URI);
        sql.append(" AND EXISTS (SELECT 1 FROM exp.propertydomain pdm");
        sql.append(" JOIN exp.domaindescriptor dd ON pdm.domainid = dd.domainid");
        sql.append(" WHERE pdm.propertyid = pd.propertyid AND dd.storagetablename IS NOT NULL)");
        sql.append(" GROUP BY pd.rangeuri");

        new SqlSelector(scope, sql).forEach(rs -> {
            String rangeUri = rs.getString("rangeuri");
            String type = null;
            if (PropertyType.STRING.getTypeUri().equals(rangeUri))
                type = TYPE_TEXT_CHOICE;
            else if (PropertyType.MULTI_CHOICE.getTypeUri().equals(rangeUri))
                type = TYPE_MVTC;

            if (type != null)
            {
                Map<String, Long> inner = counts.get(type);
                for (String ck : CHAR_KEYS)
                    inner.put(ck, rs.getLong(ck.toLowerCase()));
            }
        });
    }

    private void collectObjectPropertyMetrics(DbScope scope, Map<String, Map<String, Long>> counts)
    {
        SQLFragment sql = new SQLFragment("SELECT");
        boolean first = true;
        for (String ck : CHAR_KEYS)
        {
            sql.append(first ? " " : ", ");
            first = false;
            sql.append("COUNT(DISTINCT CASE WHEN (");
            appendBoolExpr(sql, ck, new SQLFragment("stringvalue"));
            sql.append(") THEN propertyid END) AS ").append(ck.toLowerCase());
        }
        sql.append(" FROM exp.objectproperty WHERE stringvalue IS NOT NULL");

        Map<String, Object> row = new SqlSelector(scope, sql).getMap();
        if (row != null)
        {
            Map<String, Long> inner = counts.get(TYPE_OBJECT_STRING_VALUE);
            for (String ck : CHAR_KEYS)
            {
                Object v = row.get(ck.toLowerCase());
                inner.put(ck, v instanceof Number ? ((Number) v).longValue() : 0L);
            }
        }
    }

    private void collectTextFieldMetrics(DbScope scope, SqlDialect dialect, Map<String, Map<String, Long>> counts, Logger log)
    {
        // Enumerate provisioned Text/Multiline columns, excluding Text Choice fields.
        SQLFragment enumSql = new SQLFragment(
            "SELECT dd.storageschemaname, dd.storagetablename, pd.storagecolumnname, pd.name, pd.rangeuri AS rangeuri\n" +
            "FROM exp.propertydescriptor pd\n" +
            "JOIN exp.propertydomain pdm ON pd.propertyid = pdm.propertyid\n" +
            "JOIN exp.domaindescriptor dd ON pdm.domainid = dd.domainid\n" +
            "WHERE dd.storagetablename IS NOT NULL\n" +
            "AND pd.storagecolumnname IS NOT NULL\n" +
            "AND pd.rangeuri IN (?, ?)\n").add(PropertyType.STRING.getTypeUri()).add(PropertyType.MULTI_LINE.getTypeUri());
        enumSql.append("AND NOT EXISTS (SELECT 1 FROM exp.propertyvalidator pv WHERE pv.propertyid = pd.propertyid AND pv.typeuri = ?)").add(TEXT_CHOICE_TYPE_URI);
        enumSql.append(" AND lower(dd.storageschemaname) <> ?").add(AUDIT_SCHEMA_NAME);

        // Group columns by provisioned table so each table is scanned once.
        Map<TableKey, List<Col>> byTable = new LinkedHashMap<>();
        new SqlSelector(scope, enumSql).forEach(rs -> {
            String rangeUri = rs.getString("rangeuri");
            String fieldType = PropertyType.MULTI_LINE.getTypeUri().equals(rangeUri) ? TYPE_MULTILINE : TYPE_TEXT;
            TableKey key = new TableKey(rs.getString("storageschemaname"), rs.getString("storagetablename"));
            byTable.computeIfAbsent(key, k -> new ArrayList<>()).add(new Col(rs.getString("storagecolumnname"), rs.getString("name"), fieldType));
        });

        // Sample type and data class provisioned tables have a base "name" column that is not a domain property (so it
        // is absent from exp.propertydescriptor); include it as a Text field. GitHub Issue 1086.
        addBaseNameColumns(scope, byTable);

        for (Map.Entry<TableKey, List<Col>> entry : byTable.entrySet())
            scanTable(scope, dialect, entry.getKey(), entry.getValue(), counts, log);
    }

    /**
     * Add the base "name" column for every provisioned sample type and data class table, tracked under the
     * "dataName" category. This column is not a domain property, so it is absent from the Part B enumeration.
     */
    private void addBaseNameColumns(DbScope scope, Map<TableKey, List<Col>> byTable)
    {
        SQLFragment sql = new SQLFragment(
            "SELECT dd.storageschemaname, dd.storagetablename\n" +
            "FROM exp.domaindescriptor dd\n" +
            "WHERE dd.storagetablename IS NOT NULL AND dd.storageschemaname IN (?, ?)")
            .add(SampleTypeDomainKind.PROVISIONED_SCHEMA_NAME).add(DataClassDomainKind.PROVISIONED_SCHEMA_NAME);

        new SqlSelector(scope, sql).forEach(rs -> {
            TableKey key = new TableKey(rs.getString("storageschemaname"), rs.getString("storagetablename"));
            List<Col> cols = byTable.computeIfAbsent(key, k -> new ArrayList<>());
            if (cols.stream().noneMatch(col -> "name".equalsIgnoreCase(col.storageColumnName())))
                cols.add(new Col("name", "name", TYPE_DATA_NAME));
        });
    }

    private void scanTable(DbScope scope, SqlDialect dialect, TableKey table, List<Col> cols, Map<String, Map<String, Long>> counts, Logger log)
    {
        SQLFragment sql = new SQLFragment("SELECT ");
        Map<String, String[]> aliasMeta = new LinkedHashMap<>();
        boolean first = true;

        Map<String, String> remappedCols = new CaseInsensitiveHashMap<>();
        boolean needsRemap = false;
        for (Col col : cols)
        {
            // GitHub Issue 1449: A legacy descriptor can carry a storage name that is the field name uniquified with a numeric suffix (e.g. "Container1") but never became a physical column
            String storageColumnName = col.storageColumnName();
            if (isNumericSuffixOf(storageColumnName, col.colName()))
            {
                needsRemap = true;
                break;
            }
        }

        if (needsRemap)
        {
            TableInfo ti = DbSchema.get(table.schema(), DbSchemaType.Provisioned).getTable(table.table());
            if (ti != null)
            {
                Set<String> columnNames = new CaseInsensitiveHashSet(ti.getColumnNameSet());
                for (Col col : cols)
                {
                    String storageColumnName = col.storageColumnName();
                    if (isNumericSuffixOf(storageColumnName, col.colName()) && !columnNames.contains(storageColumnName) && columnNames.contains(col.colName()))
                        remappedCols.put(storageColumnName, col.colName());
                }
            }
        }

        for (int i = 0; i < cols.size(); i++)
        {
            Col col = cols.get(i);
            String storageColumnName = col.storageColumnName();
            if (remappedCols.containsKey(storageColumnName))
                storageColumnName = remappedCols.get(storageColumnName);

            SQLFragment colRef = PropertyDescriptor.getLegalSelectNameFromStorageName(dialect, storageColumnName).getSql();
            for (String ck : CHAR_KEYS)
            {
                String alias = "c" + i + "_" + ck.toLowerCase();
                if (!first)
                    sql.append(", ");
                first = false;
                sql.append("bool_or(");
                appendBoolExpr(sql, ck, colRef);
                sql.append(") AS ").append(alias);
                aliasMeta.put(alias, new String[]{col.fieldType(), ck});
            }
        }
        sql.append(" FROM ").appendIdentifier(PropertyDescriptor.getLegalSelectNameFromStorageName(dialect, table.schema())).append(".").appendIdentifier(PropertyDescriptor.getLegalSelectNameFromStorageName(dialect, table.table()));

        try
        {
            Map<String, Object> row = new SqlSelector(scope, sql).getMap();
            if (row == null)
                return;
            for (Map.Entry<String, String[]> e : aliasMeta.entrySet())
            {
                if (Boolean.TRUE.equals(row.get(e.getKey())))
                {
                    String fieldType = e.getValue()[0];
                    String charKey = e.getValue()[1];
                    counts.get(fieldType).merge(charKey, 1L, Long::sum);
                }
            }
        }
        catch (Exception e)
        {
            log.error("Special character scan failed for provisioned table {}.{}.", table.schema(), table.table(), e);
        }
    }

    // True when storageName is name followed by a positive-integer suffix, compared case-insensitively (e.g. "Container1" for "Container").
    // Prior to https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=29047, suffix (1, 2, etc.) would be added to field that matches base fields
    private static boolean isNumericSuffixOf(String storageName, String name)
    {
        if (name == null || storageName.length() <= name.length() || !storageName.regionMatches(true, 0, name, 0, name.length()))
            return false;
        return storageName.substring(name.length()).chars().allMatch(Character::isDigit);
    }

    // Appends a boolean SQL expression detecting the given special character.
    // The search patterns are bound as parameters so that no semicolons or quotes appear in the SQL text, which SQLFragment rejects.
    // None of the target characters are LIKE wildcards, so they need no LIKE escaping.
    private static void appendBoolExpr(SQLFragment sql, String key, SQLFragment colRef)
    {
        switch (key)
        {
            case "semicolon" -> sql.append(colRef).append(" LIKE ?").add("%;%");
            case "comma" -> sql.append(colRef).append(" LIKE ?").add("%,%");
            case "newline" -> sql.append(colRef).append(" LIKE ? OR ").append(colRef).append(" LIKE ?").add("%\n%").add("%\r%");
            case "doubleQuote" -> sql.append(colRef).append(" LIKE ?").add("%\"%");
            default -> sql.append("FALSE");
        }
    }

    private record TableKey(String schema, String table) {}

    private record Col(String storageColumnName, String colName, String fieldType) {}

    public static class TestCase extends Assert
    {
        private static final String ST_NAME = "SpecialCharMetricsTest";

        @Before
        @After
        public void cleanup()
        {
            JunitUtil.deleteTestContainer();
        }

        @Test
        public void testSpecialCharacterMetrics() throws Exception
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            Logger log = LogManager.getLogger(TestCase.class);
            SpecialCharacterMetricsMaintenanceTask task = new SpecialCharacterMetricsMaintenanceTask();

            // The metric is site-wide, so capture baseline counts and assert on deltas attributable to our fields.
            task.run(log);
            Map<String, Object> metrics = SpecialCharacterMetricsProvider.getInstance().getUsageMetrics();
            long baseTextComma = count(metrics, TYPE_TEXT, "comma");
            long baseTextDquote = count(metrics, TYPE_TEXT, "doubleQuote");
            long baseTextSemicolon = count(metrics, TYPE_TEXT, "semicolon");
            long baseMultiNewline = count(metrics, TYPE_MULTILINE, "newline");
            long baseChoiceComma = count(metrics, TYPE_TEXT_CHOICE, "comma");
            long baseChoiceSemicolon = count(metrics, TYPE_TEXT_CHOICE, "semicolon");
            long baseChoiceDquote = count(metrics, TYPE_TEXT_CHOICE, "doubleQuote");
            long baseChoiceNewline = count(metrics, TYPE_TEXT_CHOICE, "newline");
            long baseDataNameComma = count(metrics, TYPE_DATA_NAME, "comma");
            long baseDataNameSemicolon = count(metrics, TYPE_DATA_NAME, "semicolon");
            long baseDataNameDquote = count(metrics, TYPE_DATA_NAME, "doubleQuote");

            // Provisioned sample type with a plain Text field, a Multiline field, and a Text Choice field.
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("Name", "http://www.w3.org/2001/XMLSchema#string"));
            props.add(new GWTPropertyDescriptor("Prop", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("Notes", PropertyType.MULTI_LINE.getTypeUri()));

            GWTPropertyDescriptor choice = new GWTPropertyDescriptor("Choice", PropertyType.STRING.getTypeUri());
            GWTPropertyValidator tc = new GWTPropertyValidator();
            tc.setName("Status Choices");
            tc.setType(PropertyValidatorType.TextChoice);
            // Choice values contain comma, double-quote and semicolon (but no newline).
            tc.setExpression("Ac,tive|In\"active|Se;mi");
            choice.getPropertyValidators().add(tc);
            props.add(choice);

            ExpSampleType st = SampleTypeService.get().createSampleType(c, user, ST_NAME, null, props,
                    Collections.emptyList(), -1, -1, -1, -1, null);
            assertNotNull("sample type should be created", st);

            // Rows whose Text/Multiline values contain the special characters.
            UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
            TableInfo table = schema.getTable(ST_NAME);
            assertNotNull("provisioned table should exist", table);
            QueryUpdateService qus = table.getUpdateService();
            assertNotNull("update service should exist", qus);

            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(CaseInsensitiveHashMap.of("name", "sc,m;1", "Prop", "a,b", "Notes", "line1\nline2", "Choice", "Ac,tive"));
            rows.add(CaseInsensitiveHashMap.of("name", "sc,m;2", "Prop", "x\"y", "Notes", "plain", "Choice", "Ac,tive"));
            BatchValidationException errors = new BatchValidationException();
            qus.insertRows(user, c, rows, errors, null, null);
            if (errors.hasErrors())
                throw errors;

            // recompute after populating samples
            task.run(log);
            metrics = SpecialCharacterMetricsProvider.getInstance().getUsageMetrics();

            assertEquals("Text comma", baseTextComma + 1, count(metrics, TYPE_TEXT, "comma"));
            assertEquals("Text doubleQuote", baseTextDquote + 1, count(metrics, TYPE_TEXT, "doubleQuote"));
            assertEquals("Text semicolon unchanged", baseTextSemicolon, count(metrics, TYPE_TEXT, "semicolon"));

            assertEquals("MultiLine newline", baseMultiNewline + 1, count(metrics, TYPE_MULTILINE, "newline"));

            assertEquals("TextChoice comma", baseChoiceComma + 1, count(metrics, TYPE_TEXT_CHOICE, "comma"));
            assertEquals("TextChoice semicolon", baseChoiceSemicolon + 1, count(metrics, TYPE_TEXT_CHOICE, "semicolon"));
            assertEquals("TextChoice doubleQuote", baseChoiceDquote + 1, count(metrics, TYPE_TEXT_CHOICE, "doubleQuote"));
            assertEquals("TextChoice newline unchanged", baseChoiceNewline, count(metrics, TYPE_TEXT_CHOICE, "newline"));

            assertEquals("dataName comma", baseDataNameComma + 1, count(metrics, TYPE_DATA_NAME, "comma"));
            assertEquals("dataName semicolon", baseDataNameSemicolon + 1, count(metrics, TYPE_DATA_NAME, "semicolon"));
            assertEquals("dataName doubleQuote unchanged", baseDataNameDquote, count(metrics, TYPE_DATA_NAME, "doubleQuote"));
        }

        @SuppressWarnings("unchecked")
        private static long count(Map<String, Object> metrics, String type, String charKey)
        {
            Object inner = metrics.get(SpecialCharacterMetricsProvider.METRIC_KEY);
            if (!(inner instanceof Map))
                return 0L;
            Object typeMap = ((Map<String, Object>) inner).get(type);
            if (!(typeMap instanceof Map))
                return 0L;
            Object v = ((Map<String, Object>) typeMap).get(charKey);
            return v instanceof Number ? ((Number) v).longValue() : 0L;
        }
    }
}