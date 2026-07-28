/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentService.DataTypeForExclusion;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.experiment.SampleTypeAuditProvider;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.labkey.api.util.IntegerUtils.asLong;

public class DataColorTable extends FilteredTable<ExpSchema>
{
    public DataColorTable(ExpSchema schema, ContainerFilter cf)
    {
        super(ExperimentServiceImpl.get().getTinfoDataColors(), schema, cf);
        setName(ExpSchema.TableType.DataColors.name());
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if ("Container".equalsIgnoreCase(name))
                continue;
            var col = addWrapColumn(baseColumn);
            if ("RowId".equalsIgnoreCase(name))
                col.setHidden(true);
        }
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm == ReadPermission.class ? perm : AdminPermission.class);
    }

    @Override
    public @Nullable QueryUpdateService getUpdateService()
    {
        return new DataColorUpdateService(this);
    }

    private static class DataColorUpdateService extends DefaultQueryUpdateService
    {
        private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
        private static final int MAX_LABEL_LENGTH = 64; // matches exp.DataColors.Label VARCHAR(64)

        public DataColorUpdateService(FilteredTable table)
        {
            super(table, table.getRealTable());
        }

        private boolean isBlankLabel(Map<String, Object> row, boolean allowMissing)
        {
            if (allowMissing && !row.containsKey("label"))
                return false;
            return StringUtils.isBlank((String) row.get("label"));
        }

        private boolean isInvalidColor(Map<String, Object> row, boolean allowMissing)
        {
            if (allowMissing && !row.containsKey("color"))
                return false;
            String color = (String) row.get("color");
            return color == null || !COLOR_PATTERN.matcher(color).matches();
        }

        private boolean isDuplicateLabel(String label, Container container, int currentRowId)
        {
            for (DataColor color : DataColorManager.getInstance().getColors(container))
            {
                if (color.getRowId() != currentRowId && color.getLabel().equalsIgnoreCase(label))
                    return true;
            }
            return false;
        }

        private long getColorCount(Container container)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            return new TableSelector(ExperimentServiceImpl.get().getTinfoDataColors(), filter, null).getRowCount();
        }

        // Shared insert/update validation. On insert the label and color are required and a duplicate label is always
        // rejected; on update only the provided fields are validated, and the duplicate check runs (excluding the row
        // itself) only when the label is being changed. The 200-color cap is insert-only and stays in insertRow.
        private void validateColor(Map<String, Object> row, Container container, boolean isInsert) throws QueryUpdateServiceException
        {
            boolean allowMissing = !isInsert;
            if (isBlankLabel(row, allowMissing))
                throw new QueryUpdateServiceException("Label cannot be blank.");
            String label = (String) row.get("label");
            if (label != null && label.length() > MAX_LABEL_LENGTH)
                throw new QueryUpdateServiceException("Label may not exceed " + MAX_LABEL_LENGTH + " characters.");
            if (isInvalidColor(row, allowMissing))
                throw new QueryUpdateServiceException("Color must be a 6-digit hex value (e.g. #1a2b3c).");
            if (isInsert || row.containsKey("label"))
            {
                int currentRowId = isInsert ? -1 : (int) row.get("rowId");
                if (isDuplicateLabel(String.valueOf(row.get("label")), container, currentRowId))
                    throw new QueryUpdateServiceException("Label '" + row.get("label") + "' is already in use.");
            }
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            validateColor(row, container, true);
            if (getColorCount(container) >= DataColorManager.MAX_DATA_COLORS)
                throw new QueryUpdateServiceException("Cannot add more than " + DataColorManager.MAX_DATA_COLORS + " colors.");

            Map<String, Object> inserted;
            try (DbScope.Transaction tx = ExperimentServiceImpl.getExpSchema().getScope().ensureTransaction())
            {
                inserted = super.insertRow(user, container, row);
                tx.addCommitTask(() -> DataColorManager.getInstance().clearCache(container), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                tx.commit();
            }
            return inserted;
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, boolean allowOwner, boolean retainCreation) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            validateColor(row, container, false);

            Map<String, Object> updated;
            try (DbScope.Transaction tx = ExperimentServiceImpl.getExpSchema().getScope().ensureTransaction())
            {
                updated = super.updateRow(user, container, row, oldRow, allowOwner, retainCreation);
                tx.addCommitTask(() -> DataColorManager.getInstance().clearCache(container), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                tx.commit();
            }
            return updated;
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            long rowId = asLong(oldRowMap.get("rowId"));
            if (DataColorManager.getInstance().isInUse(rowId))
                throw new QueryUpdateServiceException("This color can't be deleted because it is in use.");

            Map<String, Object> deleted;
            try (DbScope.Transaction tx = ExperimentServiceImpl.getExpSchema().getScope().ensureTransaction())
            {
                // Drop the per-data-type exclusion rows that reference this color BEFORE deleting the color itself:
                // exp.DataTypeColorExclusion.ColorRowId has a (RESTRICT) FK to exp.DataColors, so the color row can't
                // be removed while exclusion rows still point at it.
                ExperimentServiceImpl.get().removeDataColorExclusionsForColor(rowId);
                deleted = super.deleteRow(user, container, oldRowMap);
                tx.addCommitTask(() -> DataColorManager.getInstance().clearCache(container), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                tx.commit();
            }
            return deleted;
        }
    }

    /**
     * Integration tests for the Custom Sample Colors server logic: the {@code exp.DataColors} QueryUpdateService
     * validation (above), the {@code exp.DataTypeColorExclusion} service methods, create-path exclusion persistence +
     * audit, and orphan cleanup on color / sample-type / container deletion. Registered in
     * {@code ExperimentModule.getIntegrationTests()}.
     */
    @SuppressWarnings("JUnitMalformedDeclaration")
    public static class TestCase extends Assert
    {
        private Container _c;
        private User _user;

        @Before
        public void setUp()
        {
            JunitUtil.deleteTestContainer();
            _c = JunitUtil.getTestContainer();
            _user = TestContext.get().getUser();
        }

        @After
        public void tearDown()
        {
            JunitUtil.deleteTestContainer();
        }

        // ---- helpers --------------------------------------------------------

        private QueryUpdateService colorQus(Container c)
        {
            UserSchema schema = QueryService.get().getUserSchema(_user, c, ExpSchema.SCHEMA_NAME);
            return schema.getTable(ExpSchema.TableType.DataColors.name()).getUpdateService();
        }

        private static Map<String, Object> colorRow(String label, String color, boolean archived)
        {
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("Label", label);
            row.put("Color", color);
            row.put("Archived", archived);
            return row;
        }

        private long insertColor(Container c, String label, String color, boolean archived) throws Exception
        {
            BatchValidationException errors = new BatchValidationException();
            List<Map<String, Object>> inserted = colorQus(c).insertRows(_user, c, List.of(colorRow(label, color, archived)), errors, null, null);
            if (errors.hasErrors())
                throw errors;
            return ((Number) new CaseInsensitiveHashMap<>(inserted.get(0)).get("RowId")).longValue();
        }

        private long insertColor(String label, String color, boolean archived) throws Exception
        {
            return insertColor(_c, label, color, archived);
        }

        private void deleteColor(long rowId) throws Exception
        {
            colorQus(_c).deleteRows(_user, _c, List.of(CaseInsensitiveHashMap.of("RowId", rowId)), null, null);
        }

        private void assertColorInsertFails(Map<String, Object> row, String expectedFragment)
        {
            try
            {
                BatchValidationException errors = new BatchValidationException();
                colorQus(_c).insertRows(_user, _c, List.of(row), errors, null, null);
                if (errors.hasErrors())
                    throw errors;
                fail("Expected color insert to be rejected: " + row);
            }
            catch (Exception e)
            {
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                assertTrue("Unexpected error message: " + e.getMessage(), msg.contains(expectedFragment.toLowerCase()));
            }
        }

        private ExpSampleType createSampleType(String name) throws Exception
        {
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("Name", "string"));
            return SampleTypeService.get().createSampleType(_c, _user, name, null, props, Collections.emptyList(), -1, -1, -1, -1, null);
        }

        private void insertSample(ExpSampleType st, String name) throws Exception
        {
            UserSchema schema = QueryService.get().getUserSchema(_user, _c, SchemaKey.fromParts("Samples"));
            QueryUpdateService qus = schema.getTable(st.getName()).getUpdateService();
            BatchValidationException errors = new BatchValidationException();
            qus.insertRows(_user, _c, List.of(CaseInsensitiveHashMap.of("Name", name)), errors, null, null);
            if (errors.hasErrors())
                throw errors;
        }

        private static Map<String, Object> sampleRow(String name, Long colorRowId)
        {
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("Name", name);
            row.put("ExpMaterialColor", colorRowId);
            return row;
        }

        private String saveSample(ExpSampleType st, Map<String, Object> row, boolean isUpdate)
        {
            try
            {
                UserSchema schema = QueryService.get().getUserSchema(_user, _c, SchemaKey.fromParts("Samples"));
                QueryUpdateService qus = schema.getTable(st.getName()).getUpdateService();
                BatchValidationException errors = new BatchValidationException();
                if (isUpdate)
                    qus.updateRows(_user, _c, List.of(row), null, errors, null, null);
                else
                    qus.insertRows(_user, _c, List.of(row), errors, null, null);
                return errors.hasErrors() ? errors.getMessage() : null;
            }
            catch (Exception e)
            {
                return e.getMessage();
            }
        }

        private long countInContainer(TableInfo table, String containerId)
        {
            SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ").append(table).append(" WHERE Container = ?").add(containerId);
            return new SqlSelector(ExperimentServiceImpl.getExpSchema(), sql).getObject(Long.class);
        }

        // ---- DataColorTable QUS validation ----------------------------------

        @Test
        public void testColorValidation() throws Exception
        {
            long red = insertColor("Red", "#ff0000", false);
            assertTrue(red > 0);

            assertColorInsertFails(colorRow("", "#00ff00", false), "blank");           // blank label
            assertColorInsertFails(colorRow("BadHex", "red", false), "hex");            // not hex
            assertColorInsertFails(colorRow("BadHex2", "#ff00", false), "hex");         // too short
            assertColorInsertFails(colorRow("BadHex3", "#GGGGGG", false), "hex");       // non-hex chars
            assertColorInsertFails(colorRow("RED", "#0000ff", false), "already in use"); // case-insensitive dup
        }

        @Test
        public void testColorCapEnforced() throws Exception
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i <= DataColorManager.MAX_DATA_COLORS; i++)   // MAX + 1 rows
                rows.add(colorRow("Color" + i, String.format("#0000%02x", i % 256), false));

            try
            {
                BatchValidationException errors = new BatchValidationException();
                colorQus(_c).insertRows(_user, _c, rows, errors, null, null);
                if (errors.hasErrors())
                    throw errors;
                fail("Expected the " + DataColorManager.MAX_DATA_COLORS + "-color cap to be enforced");
            }
            catch (Exception e)
            {
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                assertTrue("Unexpected error message: " + e.getMessage(), msg.contains("more than"));
            }
        }

        @Test
        public void testInUseDeleteGuard() throws Exception
        {
            long red = insertColor("InUse", "#123456", false);
            ExpSampleType st = createSampleType("ColorInUseST");
            insertSample(st, "s1");

            ExpMaterial m = st.getSample(_c, "s1");
            m.setSampleColorId(red);
            m.save(_user);
            assertTrue("color should be reported in use", DataColorManager.getInstance().isInUse(red));

            try
            {
                deleteColor(red);
                fail("Expected in-use color delete to be rejected");
            }
            catch (Exception e)
            {
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                assertTrue("Unexpected error message: " + e.getMessage(), msg.contains("in use"));
            }

            // clear the reference and the delete should now succeed
            m.setSampleColorId(null);
            m.save(_user);
            assertFalse(DataColorManager.getInstance().isInUse(red));
            deleteColor(red);
            assertNull(DataColorManager.getInstance().getColorForRowId(_c, red));
        }

        // ---- sample import: color exclusion enforcement ---------------------

        @Test
        public void testCannotInsertSampleWithExcludedColor() throws Exception
        {
            ExpSampleType st = createSampleType("ColorInsertST");
            long red = insertColor("Red", "#ff0000", false);
            long blue = insertColor("Blue", "#0000ff", false);
            // Blue is excluded for this sample type; Red is not.
            ExperimentService.get().ensureDataColorExclusions(st.getRowId(), DataTypeForExclusion.SampleType, List.of(blue), _c, _user);

            // an allowed (non-excluded) color imports fine
            assertNull("a non-excluded color should be insertable", saveSample(st, sampleRow("okSample", red), false));
            // a null color is always fine
            assertNull("a sample with no color should be insertable", saveSample(st, sampleRow("noColor", null), false));

            // an excluded color is rejected
            String err = saveSample(st, sampleRow("badSample", blue), false);
            assertNotNull("inserting an excluded color should fail", err);
            assertTrue("Unexpected error: " + err, err.toLowerCase().contains("not valid"));
        }

        @Test
        public void testCannotUpdateSampleToExcludedColor() throws Exception
        {
            ExpSampleType st = createSampleType("ColorUpdateST");
            long red = insertColor("Red", "#ff0000", false);
            long blue = insertColor("Blue", "#0000ff", false);
            ExperimentService.get().ensureDataColorExclusions(st.getRowId(), DataTypeForExclusion.SampleType, List.of(blue), _c, _user);

            assertNull(saveSample(st, sampleRow("s1", red), false));
            long sampleRowId = st.getSample(_c, "s1").getRowId();

            // updating to the excluded color is rejected
            Map<String, Object> toExcluded = new CaseInsensitiveHashMap<>();
            toExcluded.put("RowId", sampleRowId);
            toExcluded.put("ExpMaterialColor", blue);
            String err = saveSample(st, toExcluded, true);
            assertNotNull("updating to an excluded color should fail", err);
            assertTrue("Unexpected error: " + err, err.toLowerCase().contains("not valid"));

            // updating to an allowed color succeeds
            Map<String, Object> toAllowed = new CaseInsensitiveHashMap<>();
            toAllowed.put("RowId", sampleRowId);
            toAllowed.put("ExpMaterialColor", red);
            assertNull("updating to a non-excluded color should succeed", saveSample(st, toAllowed, true));
        }

        @Test
        public void testArchivedNonExcludedColorCanBeImported() throws Exception
        {
            ExpSampleType st = createSampleType("ColorArchivedImportST");
            long blue = insertColor("Blue", "#0000ff", false);
            long gray = insertColor("Gray", "#888888", true); // archived, but NOT excluded
            // Exclude Blue so the import check is active for this type.
            ExperimentService.get().ensureDataColorExclusions(st.getRowId(), DataTypeForExclusion.SampleType, List.of(blue), _c, _user);

            // Exclusion — not archived-ness — is what's enforced on import, so an archived non-excluded color is allowed.
            assertNull("an archived non-excluded color should still be insertable", saveSample(st, sampleRow("s1", gray), false));
        }

        // Note: resolving a color by its Label (and rejecting a wrong-case or nonexistent label) is an import-path
        // concern — the label -> rowId remap happens in the ETL/import DataIterator, not in a direct QUS insertRows
        // (which expects the ExpMaterialColor key). That coverage lives in the remoteapi SMSampleColorsApiTest, which
        // imports by label via QueryApiHelper.importData.

        // ---- exclusion service methods --------------------------------------

        @Test
        public void testExclusionDeltaAndReads() throws Exception
        {
            ExperimentService svc = ExperimentService.get();
            ExpSampleType stA = createSampleType("ExclA");
            ExpSampleType stB = createSampleType("ExclB");
            long red = insertColor("Red", "#ff0000", false);
            long blue = insertColor("Blue", "#0000ff", false);
            long typeA = stA.getRowId();
            long typeB = stB.getRowId();

            assertTrue(svc.getDataTypeExcludedColors(DataTypeForExclusion.SampleType, typeA).isEmpty());

            // exclude red for stA
            Set<Long> affected = svc.updateColorDataTypeExclusions(red, DataTypeForExclusion.SampleType, List.of(typeA), null, _c, _user);
            assertEquals(Set.of(typeA), affected);
            assertEquals(Set.of(red), svc.getDataTypeExcludedColors(DataTypeForExclusion.SampleType, typeA));
            assertEquals(Set.of(typeA), svc.getDataTypesExcludingColor(DataTypeForExclusion.SampleType, red));

            // the exclusion row is stamped with the caller's container
            String stampedContainer = new SqlSelector(ExperimentServiceImpl.getExpSchema(),
                    new SQLFragment("SELECT Container FROM ").append(ExperimentServiceImpl.get().getTinfoDataTypeColorExclusion())
                            .append(" WHERE ColorRowId = ? AND DataTypeRowId = ?").add(red).add(typeA)).getObject(String.class);
            assertEquals(_c.getId(), stampedContainer);

            // idempotent re-add is a no-op
            assertTrue(svc.updateColorDataTypeExclusions(red, DataTypeForExclusion.SampleType, List.of(typeA), null, _c, _user).isEmpty());

            // getActiveDataTypeColors reflects the exclusion for stA only
            assertFalse(svc.getActiveDataTypeColors(_c, DataTypeForExclusion.SampleType, typeA).contains(red));
            assertTrue(svc.getActiveDataTypeColors(_c, DataTypeForExclusion.SampleType, typeB).contains(red));

            // re-enable (delta remove)
            affected = svc.updateColorDataTypeExclusions(red, DataTypeForExclusion.SampleType, null, List.of(typeA), _c, _user);
            assertEquals(Set.of(typeA), affected);
            assertTrue(svc.getDataTypeExcludedColors(DataTypeForExclusion.SampleType, typeA).isEmpty());

            // ensureDataColorExclusions sets the full disabled set (create-time path)
            assertTrue(svc.ensureDataColorExclusions(typeB, DataTypeForExclusion.SampleType, List.of(blue), _c, _user));
            assertEquals(Set.of(blue), svc.getDataTypeExcludedColors(DataTypeForExclusion.SampleType, typeB));
            // ...and is idempotent
            assertFalse(svc.ensureDataColorExclusions(typeB, DataTypeForExclusion.SampleType, List.of(blue), _c, _user));
        }

        // ---- create-path persistence + audit --------------------------------

        @Test
        public void testCreateSampleTypeWithExclusionsAndAudit() throws Exception
        {
            long red = insertColor("Red", "#ff0000", false);

            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("Name", "string"));
            ExpSampleType st = SampleTypeService.get().createSampleType(_c, _user, "CreatedWithColors", null, props,
                    Collections.emptyList(), -1, -1, -1, -1,
                    null, null, null, null, null, null,      // nameExpression..metricUnit
                    null, null, null, null,                   // autoLink..disabledSystemField
                    null, null, List.of((int) red), null);    // excludedContainerIds, excludedDashboardContainerIds, excludedSampleColorIds, changeDetails

            assertEquals(Set.of(red), ExperimentService.get().getDataTypeExcludedColors(DataTypeForExclusion.SampleType, st.getRowId()));
            assertColorAuditEventWritten();
        }

        @Test
        public void testAuditSampleColorExclusion() throws Exception
        {
            ExpSampleType st = createSampleType("AuditST");
            long red = insertColor("Red", "#ff0000", false);
            ExperimentService.get().updateColorDataTypeExclusions(red, DataTypeForExclusion.SampleType, List.of((long) st.getRowId()), null, _c, _user);
            SampleTypeService.get().auditSampleColorExclusion(_c, st.getRowId(), "junit comment", _user);
            assertColorAuditEventWritten();
        }

        private void assertColorAuditEventWritten()
        {
            List<? extends AuditTypeEvent> events = AuditLogService.get().getAuditEvents(_c, _user, SampleTypeAuditProvider.EVENT_TYPE, null, new Sort("-RowId"));
            assertTrue("expected a sample-type audit event mentioning color exclusion",
                    events.stream().anyMatch(e -> e.getComment() != null && e.getComment().contains("Sample color exclusion")));
        }

        // ---- orphan cleanup -------------------------------------------------

        @Test
        public void testNoOrphanExclusionsOnColorDelete() throws Exception
        {
            ExpSampleType st = createSampleType("OrphanColorST");
            long red = insertColor("Red", "#ff0000", false);
            ExperimentService.get().updateColorDataTypeExclusions(red, DataTypeForExclusion.SampleType, List.of((long) st.getRowId()), null, _c, _user);
            assertFalse(ExperimentService.get().getDataTypesExcludingColor(DataTypeForExclusion.SampleType, red).isEmpty());

            deleteColor(red);
            assertTrue("exclusion rows for a deleted color must be removed",
                    ExperimentService.get().getDataTypesExcludingColor(DataTypeForExclusion.SampleType, red).isEmpty());
        }

        @Test
        public void testNoOrphanExclusionsOnSampleTypeDelete() throws Exception
        {
            ExpSampleType st = createSampleType("OrphanTypeST");
            long red = insertColor("Red", "#ff0000", false);
            long typeRowId = st.getRowId();
            ExperimentService.get().updateColorDataTypeExclusions(red, DataTypeForExclusion.SampleType, List.of(typeRowId), null, _c, _user);
            assertFalse(ExperimentService.get().getDataTypeExcludedColors(DataTypeForExclusion.SampleType, typeRowId).isEmpty());

            st.delete(_user, null);
            assertTrue("exclusion rows for a deleted sample type must be removed",
                    ExperimentService.get().getDataTypeExcludedColors(DataTypeForExclusion.SampleType, typeRowId).isEmpty());
        }

        @Test
        public void testNoOrphanColorsOrExclusionsOnContainerDelete() throws Exception
        {
            Container child = ContainerManager.createContainer(_c, "SampleColorsChild", _user);
            String childId = child.getId();

            long red = insertColor(child, "ChildRed", "#ff0000", false);

            ExpSampleType st = SampleTypeService.get().createSampleType(child, _user, "ChildST", null,
                    List.of(new GWTPropertyDescriptor("Name", "string")), Collections.emptyList(), -1, -1, -1, -1, null);
            ExperimentService.get().updateColorDataTypeExclusions(red, DataTypeForExclusion.SampleType, List.of((long) st.getRowId()), null, child, _user);

            assertEquals(1L, countInContainer(ExperimentServiceImpl.get().getTinfoDataColors(), childId));
            assertEquals(1L, countInContainer(ExperimentServiceImpl.get().getTinfoDataTypeColorExclusion(), childId));

            ContainerManager.delete(child, _user);

            assertEquals("no orphaned colors after container delete", 0L, countInContainer(ExperimentServiceImpl.get().getTinfoDataColors(), childId));
            assertEquals("no orphaned exclusions after container delete", 0L, countInContainer(ExperimentServiceImpl.get().getTinfoDataTypeColorExclusion(), childId));
        }
    }

}
