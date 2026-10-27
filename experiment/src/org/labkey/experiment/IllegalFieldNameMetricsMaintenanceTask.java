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
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.DomainUtil.FieldNameRule;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * GH Issue 1589: counts stored domains whose field names break a naming rule that was introduced after they were saved.
 * Reports per {@link FieldNameRule} and domain kind so a non-zero metric says which kind to look at, without naming containers.
 *
 * Builds no Domain objects. DomainImpl loads and clones every PropertyDescriptor, and OntologyManager's domain caches
 * hold only a couple thousand entries, so walking every domain through them would evict the caches wholesale. Field rows
 * come straight from exp.* and the kind is resolved by LSID string match.
 */
public class IllegalFieldNameMetricsMaintenanceTask implements MaintenanceTask
{
    public static final String NAME = "IllegalFieldNameMetricsMaintenanceTask";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Task to calculate metrics for field names that violate current naming rules";
    }

    @Override
    public void run(Logger log)
    {
        try
        {
            DbScope scope = ExperimentService.get().getSchema().getScope();

            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("Run time", new Date());

            Map<String, Map<String, Map<String, Object>>> parentAliases = loadParentAliases(scope, log);
            Counts counts = new Counts();
            int[] domainCounts = scanDomains(scope, parentAliases, counts, log);

            metric.put("domainsScanned", domainCounts[0]);
            // Domains with no registered kind, skipped without validating. Drop this if it stays a rounding error;
            // it is here to say whether filtering them out in SQL would be worth an extra query.
            metric.put("domainsSkipped", domainCounts[1]);
            metric.putAll(counts.toMap());

            IllegalFieldNameMetricsProvider.getInstance().updateMetrics(metric);
        }
        catch (Exception e)
        {
            log.error("Unable to run illegal field name metrics task.", e);
        }
    }

    /** Parent alias headers by domain URI. A sample type's or data class's LSID is its domain's URI. */
    private Map<String, Map<String, Map<String, Object>>> loadParentAliases(DbScope scope, Logger log)
    {
        Map<String, Map<String, Map<String, Object>>> byDomainUri = new HashMap<>();
        loadParentAliases(scope, "exp.materialsource", "materialparentimportaliasmap", byDomainUri, log);
        loadParentAliases(scope, "exp.dataclass", "dataparentimportaliasmap", byDomainUri, log);
        return byDomainUri;
    }

    private void loadParentAliases(DbScope scope, String table, String aliasColumn,
            Map<String, Map<String, Map<String, Object>>> byDomainUri, Logger log)
    {
        SQLFragment sql = new SQLFragment("SELECT lsid, ");
        sql.unsafeAppend(aliasColumn).unsafeAppend(" AS aliases FROM ").unsafeAppend(table)
            .unsafeAppend(" WHERE ").unsafeAppend(aliasColumn).unsafeAppend(" IS NOT NULL");

        new SqlSelector(scope, sql).forEach(rs -> {
            String lsid = rs.getString("lsid");
            try
            {
                Map<String, Map<String, Object>> aliases = ExperimentJSONConverter.parseImportAliases(rs.getString("aliases"));
                if (!aliases.isEmpty())
                    byDomainUri.put(lsid, aliases);
            }
            catch (Exception e)
            {
                log.error("Unable to parse parent import aliases for [{}].", lsid, e);
            }
        });
    }

    /**
     * Streams every domain's fields, ordered so one domain's rows arrive together and only that domain is held at a time.
     * Returns {validated domain count, skipped domain count}.
     */
    private int[] scanDomains(DbScope scope, Map<String, Map<String, Map<String, Object>>> parentAliases, Counts counts, Logger log)
    {
        SQLFragment sql = new SQLFragment(
            "SELECT dd.domainid, dd.domainuri, dd.name AS domainname,\n" +
            "       pd.propertyid, pd.name, pd.label, pd.importaliases, pd.rangeuri, pd.concepturi\n" +
            "FROM exp.domaindescriptor dd\n" +
            "JOIN exp.propertydomain pdm ON pdm.domainid = dd.domainid\n" +
            "JOIN exp.propertydescriptor pd ON pd.propertyid = pdm.propertyid\n" +
            "ORDER BY dd.domainid, pdm.sortorder, pd.propertyid");

        DomainAccumulator acc = new DomainAccumulator(parentAliases, counts, log);
        new SqlSelector(scope, sql).forEach(rs -> {
            int domainId = rs.getInt("domainid");
            acc.startDomain(domainId, rs.getString("domainuri"), rs.getString("domainname"));
            if (acc.isSkipping())
                return;

            GWTPropertyDescriptor pd = new GWTPropertyDescriptor();
            pd.setPropertyId(rs.getInt("propertyid"));
            pd.setName(rs.getString("name"));
            pd.setLabel(rs.getString("label"));
            pd.setImportAliases(rs.getString("importaliases"));
            pd.setRangeURI(rs.getString("rangeuri"));
            pd.setConceptURI(rs.getString("concepturi"));
            acc.addField(pd);
        });
        acc.flush();

        return new int[]{acc.getDomainCount(), acc.getSkippedCount()};
    }

    /** Holds one domain's rows, then validates and discards it when the next domain's rows start arriving. */
    private static class DomainAccumulator
    {
        private final Map<String, Map<String, Map<String, Object>>> _parentAliases;
        private final Counts _counts;
        private final Logger _log;

        private final List<GWTPropertyDescriptor> _fields = new ArrayList<>();
        private int _domainId = -1;
        private String _domainUri;
        private String _domainName;
        private DomainKind<?> _kind;
        private int _domainCount = 0;
        private int _skippedCount = 0;

        DomainAccumulator(Map<String, Map<String, Map<String, Object>>> parentAliases, Counts counts, Logger log)
        {
            _parentAliases = parentAliases;
            _counts = counts;
            _log = log;
        }

        void startDomain(int domainId, String domainUri, String domainName)
        {
            if (domainId != _domainId)
            {
                flush();
                _domainId = domainId;
                _domainUri = domainUri;
                _domainName = domainName;
                // Resolved once per domain rather than after accumulating its fields, so a domain we don't handle costs
                // no descriptor objects. Pure LSID string matching, so this touches no cache and no database.
                _kind = PropertyService.get().getDomainKind(domainUri);
            }
        }

        /** True when the current domain has no registered kind, so its rows carry nothing we can validate. */
        boolean isSkipping()
        {
            return _kind == null;
        }

        void addField(GWTPropertyDescriptor pd)
        {
            _fields.add(pd);
        }

        int getDomainCount()
        {
            return _domainCount;
        }

        int getSkippedCount()
        {
            return _skippedCount;
        }

        void flush()
        {
            if (_domainId == -1)
                return;

            if (isSkipping())
            {
                _skippedCount++;
                return;
            }

            _domainCount++;
            try
            {
                validate();
            }
            catch (Exception e)
            {
                _log.error("Illegal field name scan failed for domain [{}] {}.", _domainId, _domainUri, e);
            }
            _fields.clear();
        }

        private void validate()
        {
            String kindName = _kind.getKindName();

            GWTDomain<GWTPropertyDescriptor> gwtDomain = new GWTDomain<>();
            gwtDomain.setName(_domainName);
            gwtDomain.setDomainURI(_domainUri);
            gwtDomain.setFields(_fields);

            // forCreate=false matters: SampleTypeDomainKind drops "Name" from the reserved set when creating, and the
            // Domain-based overload would infer forCreate from our null domain. A null domain also skips the kind's
            // ExpSampleType lookup, so the persisted parent aliases are unioned in below instead.
            Set<String> kindReserved = _kind.getReservedPropertyNames(null, null, false);

            // A field colliding with a parent alias header is counted once, as parentAliasConflict below, rather than
            // also folding the headers into the reserved set here and reporting the same collision under two rules.
            Map<String, Map<String, Object>> aliases = _parentAliases.get(_domainUri);

            DomainUtil.validateProperties(gwtDomain, kindReserved, _kind.getReservedPropertyNamePrefixes(),
                _kind.allowMultiChoiceProperties(), _kind.supportsNamingPattern(),
                    null /*null on purpose to not forgive an existing field that keeps a name that only later became reserved*/,
                (rule, _) -> _counts.add(rule.name(), kindName));

            if (aliases != null)
                validateParentAliases(aliases, kindReserved, gwtDomain, kindName);
        }

        private void validateParentAliases(Map<String, Map<String, Object>> aliases, Set<String> reservedNames,
                GWTDomain<GWTPropertyDescriptor> gwtDomain, String kindName)
        {
            Map<String, String> aliasMap = new HashMap<>();
            aliases.forEach((alias, spec) -> aliasMap.put(alias, (String) spec.get("inputType")));

            try
            {
                // Fail-fast, so this counts types carrying at least one conflict rather than every conflict.
                ExperimentService.validateParentAlias(aliasMap, reservedNames, gwtDomain, kindName);
            }
            catch (IllegalArgumentException e)
            {
                _counts.add("parentAliasConflict", kindName);
            }
        }
    }

    /** rule -> domain kind -> count. */
    private static class Counts
    {
        private final Map<String, Map<String, Integer>> _counts = new TreeMap<>();

        void add(String rule, String kindName)
        {
            _counts.computeIfAbsent(rule, k -> new TreeMap<>()).merge(kindName, 1, Integer::sum);
        }

        Map<String, Object> toMap()
        {
            return new LinkedHashMap<>(_counts);
        }
    }

    public static class TestCase extends Assert
    {
        private static final String ST_NAME = "IllegalNameMetricsTest";
        private static final String KIND = "SampleSet";

        @Before
        @After
        public void cleanup()
        {
            JunitUtil.deleteTestContainer();
        }

        @Test
        public void testIllegalNameMetrics() throws Exception
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            Logger log = LogManager.getLogger(TestCase.class);
            IllegalFieldNameMetricsMaintenanceTask task = new IllegalFieldNameMetricsMaintenanceTask();

            // The metric is site-wide, so capture baselines and assert on deltas.
            task.run(log);
            Map<String, Object> metrics = IllegalFieldNameMetricsProvider.getInstance().getUsageMetrics();
            long baseReserved = count(metrics, FieldNameRule.reservedName.name());
            long baseAliasReserved = count(metrics, FieldNameRule.importAliasVsReservedName.name());
            long baseDupeAlias = count(metrics, FieldNameRule.duplicateImportAlias.name());
            long baseParentAlias = count(metrics, "parentAliasConflict");

            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("Name", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("WillBecomeReserved", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("WillAliasContainer", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("DupeAliasOne", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("DupeAliasTwo", PropertyType.STRING.getTypeUri()));

            ExpSampleType st = SampleTypeService.get().createSampleType(c, user, ST_NAME, null, props,
                    Collections.emptyList(), -1, -1, -1, -1, null);
            assertNotNull("sample type should be created", st);

            // Make the fields illegal behind the designer's back, which is the state an upgraded server is in: these
            // configurations were legal when saved and validateProperties would reject them now.
            DbScope scope = ExperimentService.get().getSchema().getScope();
            SqlExecutor executor = new SqlExecutor(scope);
            int domainId = st.getDomain().getTypeId();
            update(executor, domainId, "WillBecomeReserved", "name", "AliquotTotalVolume");
            update(executor, domainId, "WillAliasContainer", "importaliases", "Container");
            update(executor, domainId, "DupeAliasOne", "importaliases", "SharedAlias");
            update(executor, domainId, "DupeAliasTwo", "importaliases", "SharedAlias");
            OntologyManager.clearCaches();

            task.run(log);
            metrics = IllegalFieldNameMetricsProvider.getInstance().getUsageMetrics();

            assertEquals("reserved field name", baseReserved + 1, count(metrics, FieldNameRule.reservedName.name()));
            assertEquals("import alias vs reserved name", baseAliasReserved + 1, count(metrics, FieldNameRule.importAliasVsReservedName.name()));
            // Both fields declaring the shared alias are counted.
            assertEquals("duplicate import alias", baseDupeAlias + 2, count(metrics, FieldNameRule.duplicateImportAlias.name()));
            assertEquals("parent alias unchanged", baseParentAlias, count(metrics, "parentAliasConflict"));
        }

        @Test
        public void testLabelCollisionMetrics() throws Exception
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            Logger log = LogManager.getLogger(TestCase.class);
            IllegalFieldNameMetricsMaintenanceTask task = new IllegalFieldNameMetricsMaintenanceTask();

            task.run(log);
            Map<String, Object> metrics = IllegalFieldNameMetricsProvider.getInstance().getUsageMetrics();
            long baseDupeLabel = count(metrics, FieldNameRule.duplicateLabel.name());
            long baseLabelVsName = count(metrics, FieldNameRule.labelVsFieldName.name());
            long baseLabelVsAlias = count(metrics, FieldNameRule.labelVsImportAlias.name());

            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("Name", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("Shadowed", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("Winner", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("LabelTwinOne", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("LabelTwinTwo", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("AliasOwner", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("LabelStealsAlias", PropertyType.STRING.getTypeUri()));

            ExpSampleType st = SampleTypeService.get().createSampleType(c, user, ST_NAME, null, props,
                    Collections.emptyList(), -1, -1, -1, -1, null);
            assertNotNull("sample type should be created", st);

            DbScope scope = ExperimentService.get().getSchema().getScope();
            SqlExecutor executor = new SqlExecutor(scope);
            int domainId = st.getDomain().getTypeId();
            // Label loses to another field's name.
            update(executor, domainId, "Shadowed", "label", "Winner");
            // Two fields carrying one label; only the first is reachable.
            update(executor, domainId, "LabelTwinOne", "label", "SharedLabel");
            update(executor, domainId, "LabelTwinTwo", "label", "SharedLabel");
            // A label outranks this alias, so the alias becomes unreachable.
            update(executor, domainId, "AliasOwner", "importaliases", "StolenToken");
            update(executor, domainId, "LabelStealsAlias", "label", "StolenToken");
            OntologyManager.clearCaches();

            task.run(log);
            metrics = IllegalFieldNameMetricsProvider.getInstance().getUsageMetrics();

            assertEquals("duplicate label", baseDupeLabel + 2, count(metrics, FieldNameRule.duplicateLabel.name()));
            assertEquals("label vs field name", baseLabelVsName + 1, count(metrics, FieldNameRule.labelVsFieldName.name()));
            assertEquals("label vs import alias", baseLabelVsAlias + 1, count(metrics, FieldNameRule.labelVsImportAlias.name()));
        }

        @Test
        public void testLabelMatchingOwnFieldIsNotCounted() throws Exception
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            Logger log = LogManager.getLogger(TestCase.class);
            IllegalFieldNameMetricsMaintenanceTask task = new IllegalFieldNameMetricsMaintenanceTask();

            task.run(log);
            Map<String, Object> metrics = IllegalFieldNameMetricsProvider.getInstance().getUsageMetrics();
            long baseLabelVsAlias = count(metrics, FieldNameRule.labelVsImportAlias.name());
            long baseDupeLabel = count(metrics, FieldNameRule.duplicateLabel.name());

            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("Name", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("SelfOwner", PropertyType.STRING.getTypeUri()));

            ExpSampleType st = SampleTypeService.get().createSampleType(c, user, ST_NAME, null, props,
                    Collections.emptyList(), -1, -1, -1, -1, null);
            assertNotNull("sample type should be created", st);

            // One field owning both its label and the matching alias resolves to that field either way.
            DbScope scope = ExperimentService.get().getSchema().getScope();
            SqlExecutor executor = new SqlExecutor(scope);
            int domainId = st.getDomain().getTypeId();
            update(executor, domainId, "SelfOwner", "label", "OwnToken");
            update(executor, domainId, "SelfOwner", "importaliases", "OwnToken");
            OntologyManager.clearCaches();

            task.run(log);
            metrics = IllegalFieldNameMetricsProvider.getInstance().getUsageMetrics();

            assertEquals("same field owning label and alias is not a collision", baseLabelVsAlias,
                    count(metrics, FieldNameRule.labelVsImportAlias.name()));
            assertEquals("no duplicate label", baseDupeLabel, count(metrics, FieldNameRule.duplicateLabel.name()));
        }

        @Test
        public void testParentAliasConflict() throws Exception
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            Logger log = LogManager.getLogger(TestCase.class);
            IllegalFieldNameMetricsMaintenanceTask task = new IllegalFieldNameMetricsMaintenanceTask();

            task.run(log);
            long baseParentAlias = count(IllegalFieldNameMetricsProvider.getInstance().getUsageMetrics(), "parentAliasConflict");

            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("Name", PropertyType.STRING.getTypeUri()));
            props.add(new GWTPropertyDescriptor("Collision", PropertyType.STRING.getTypeUri()));

            ExpSampleType st = SampleTypeService.get().createSampleType(c, user, ST_NAME, null, props,
                    Collections.emptyList(), -1, -1, -1, -1, null);
            assertNotNull("sample type should be created", st);

            // A parent alias header that collides with a field name. Saving this through the designer is blocked, so
            // write it directly to reproduce a pre-validation configuration.
            DbScope scope = ExperimentService.get().getSchema().getScope();
            new SqlExecutor(scope).execute(new SQLFragment(
                "UPDATE exp.materialsource SET materialparentimportaliasmap = ? WHERE lsid = ?")
                .add("{\"Collision\":\"materialInputs/Whatever\"}").add(st.getLSID()));

            task.run(log);
            assertEquals("parent alias conflict", baseParentAlias + 1,
                    count(IllegalFieldNameMetricsProvider.getInstance().getUsageMetrics(), "parentAliasConflict"));
        }

        private static void update(SqlExecutor executor, int domainId, String fieldName, String column, String value)
        {
            SQLFragment sql = new SQLFragment("UPDATE exp.propertydescriptor SET ");
            sql.unsafeAppend(column).unsafeAppend(" = ?").add(value);
            sql.unsafeAppend(" WHERE name = ? AND propertyid IN (SELECT propertyid FROM exp.propertydomain WHERE domainid = ?)")
                .add(fieldName).add(domainId);
            assertEquals("expected to rewrite one field", 1, executor.execute(sql));
        }

        /** Sums a rule's per-kind counts for the sample type kind. */
        @SuppressWarnings("unchecked")
        private static long count(Map<String, Object> metrics, String rule)
        {
            Object inner = metrics.get(IllegalFieldNameMetricsProvider.METRIC_KEY);
            if (!(inner instanceof Map))
                return 0L;
            Object ruleMap = ((Map<String, Object>) inner).get(rule);
            if (!(ruleMap instanceof Map))
                return 0L;
            Object v = ((Map<String, Object>) ruleMap).get(KIND);
            return v instanceof Number ? ((Number) v).longValue() : 0L;
        }
    }
}
