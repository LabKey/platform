package org.labkey.assay.plate;

import org.apache.commons.collections4.MapUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateCustomField;
import org.labkey.api.assay.plate.PlateLayoutHandler;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateSetType;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.assay.AssayModule;
import org.labkey.assay.plate.model.ReformatOptions;
import org.labkey.assay.plate.model.WellBean;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.plate.query.WellTable;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.labkey.api.exp.query.SamplesSchema.SCHEMA_SAMPLES;
import static org.labkey.api.util.IntegerUtils.asLongElseNull;
import static org.labkey.api.util.JunitUtil.deleteTestContainer;

public final class PlateManagerTest
{
    private static Long ARCHIVED_PLATE_SET_ID;
    private static Long EMPTY_PLATE_SET_ID;
    private static Long FULL_PLATE_SET_ID;

    private static PlateType PLATE_TYPE_12_WELLS;
    private static PlateType PLATE_TYPE_96_WELLS;
    private static PlateType PLATE_TYPE_384_WELLS;

    private static Container container;
    private static ExpSampleType sampleType;
    private static User user;

    private enum PlateMetadataFields
    {
        barcode,
        description,
        negativeControl,
        opacity,
    }

    @BeforeClass
    public static void setupTest() throws Exception
    {
        deleteTestContainer();

        container = JunitUtil.getTestContainer();
        user = TestContext.get().getUser();

        Module assayModule = ModuleLoader.getInstance().getModule(AssayModule.NAME);
        Set<Module> activeModules = container.getActiveModules();

        if (!activeModules.contains(assayModule))
        {
            Set<Module> newActiveModules = new HashSet<>(activeModules);
            newActiveModules.add(assayModule);
            container.setActiveModules(newActiveModules);
        }

        Domain domain = PlateManager.get().getPlateMetadataDomain(container, user);
        if (domain != null)
            domain.delete(user);

        // create custom properties
        {
            List<GWTPropertyDescriptor> customFields = List.of(
                new GWTPropertyDescriptor(PlateMetadataFields.barcode.name(), "http://www.w3.org/2001/XMLSchema#string"),
                new GWTPropertyDescriptor(PlateMetadataFields.description.name(), "http://www.w3.org/2001/XMLSchema#string"),
                new GWTPropertyDescriptor(PlateMetadataFields.opacity.name(), "http://www.w3.org/2001/XMLSchema#double"),
                new GWTPropertyDescriptor(PlateMetadataFields.negativeControl.name(), "http://www.w3.org/2001/XMLSchema#double")
            );
            PlateManager.get().createPlateMetadataFields(container, user, customFields);
        }

        // create sample type
        {
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("name", "string"));
            sampleType = SampleTypeService.get().createSampleType(container, user, "SampleType1", null, props, emptyList(), -1, -1, -1, -1, "PMT-${genId}", null);
        }

        // resolve plate types
        {
            PLATE_TYPE_12_WELLS = PlateManager.get().getPlateType(3, 4);
            assertNotNull(PLATE_TYPE_12_WELLS);

            PLATE_TYPE_96_WELLS = PlateManager.get().getPlateType(8, 12);
            assertNotNull(PLATE_TYPE_96_WELLS);

            PLATE_TYPE_384_WELLS = PlateManager.get().getPlateType(16, 24);
            assertNotNull(PLATE_TYPE_384_WELLS);
        }

        // create archived plate set
        {
            PlateSetImpl archivedPlateSet = new PlateSetImpl();
            archivedPlateSet.setDescription("PlateManagerTest Archived Plate Set");

            archivedPlateSet = createPlateSet(archivedPlateSet, null, null);
            PlateManager.get().archive(container, user, List.of(archivedPlateSet.getRowId()), null, true);
            archivedPlateSet = (PlateSetImpl) PlateManager.get().getPlateSet(container, archivedPlateSet.getRowId());
            assertNotNull(archivedPlateSet);
            assertTrue(archivedPlateSet.isArchived());
            ARCHIVED_PLATE_SET_ID = archivedPlateSet.getRowId();
        }

        // create empty plate set
        {
            PlateSetImpl emptyPlateSet = new PlateSetImpl();
            emptyPlateSet.setDescription("PlateManagerTest Empty Plate Set");

            emptyPlateSet = createPlateSet(emptyPlateSet, null, null);
            assertEquals(Integer.valueOf(0), emptyPlateSet.getPlateCount());
            EMPTY_PLATE_SET_ID = emptyPlateSet.getRowId();
        }

        // create full plate set
        {
            PlateSetImpl fullPlateSet = new PlateSetImpl();
            fullPlateSet.setDescription("PlateManagerTest Full Plate Set");

            List<PlateManager.PlateData> fullPlates = new ArrayList<>();
            for (int i = 0; i < PlateSet.MAX_PLATES; i++)
                fullPlates.add(new PlateManager.PlateData(null, PLATE_TYPE_12_WELLS.getRowId(), null, null, null));

            fullPlateSet = createPlateSet(fullPlateSet, fullPlates, null);
            assertTrue(fullPlateSet.isFull());
            FULL_PLATE_SET_ID = fullPlateSet.getRowId();
        }
    }

    @AfterClass
    public static void cleanup()
    {
        deleteTestContainer();
        container = null;
        user = null;
    }

    @Test
    public void testCreatePlateTemplate() throws Exception
    {
        //
        // INSERT
        //

        PlateLayoutHandler handler = PlateManager.get().getPlateLayoutHandler(TsvPlateLayoutHandler.TYPE);
        PlateType plateType = PLATE_TYPE_96_WELLS;

        Plate template = handler.createPlate("UNUSED", container, plateType);
        template.setName("bob");
        template.setProperty("friendly", "yes");
        assertNull(template.getRowId());
        assertNull(template.getLSID());

        WellGroup wg1 = template.addWellGroup("wg1", WellGroup.Type.SAMPLE,
            PlateManager.get().createPosition(container, 0, 0),
            PlateManager.get().createPosition(container, 0, 11)
        );
        wg1.setProperty("score", "100");
        assertNull(wg1.getRowId());
        assertNull(wg1.getLSID());

        long plateId = PlateManager.get().save(container, user, template);

        //
        // VERIFY INSERT
        //

        assertNotNull(PlateManager.get().getPlate(container, plateId));

        Plate savedTemplate = PlateManager.get().getPlateByName(container, "bob");
        assertEquals(plateId, savedTemplate.getRowId().intValue());
        assertEquals("bob", savedTemplate.getName());
        assertEquals("yes", savedTemplate.getProperty("friendly"));
        assertNotNull(savedTemplate.getLSID());
        assertEquals(plateType.getRowId(), savedTemplate.getPlateType().getRowId());

        List<? extends WellGroup> wellGroups = savedTemplate.getWellGroups();
        assertEquals(3, wellGroups.size());

        // TsvPlateTypeHandler creates two CONTROL well groups "Positive" and "Negative"
        List<? extends WellGroup> controlWellGroups = savedTemplate.getWellGroups(WellGroup.Type.CONTROL);
        assertEquals(2, controlWellGroups.size());

        List<? extends WellGroup> sampleWellGroups = savedTemplate.getWellGroups(WellGroup.Type.SAMPLE);
        assertEquals(1, sampleWellGroups.size());
        WellGroup savedWg1 = sampleWellGroups.getFirst();
        assertEquals("wg1", savedWg1.getName());
        assertEquals("100", savedWg1.getProperty("score"));

        List<Position> savedWg1Positions = savedWg1.getPositions();
        assertEquals(12, savedWg1Positions.size());

        //
        // UPDATE
        //

        // rename plate
        savedTemplate.setName("sally");

        // add well group
        WellGroup wg2 = savedTemplate.addWellGroup("wg2", WellGroup.Type.SAMPLE,
                PlateManager.get().createPosition(container, 1, 0),
                PlateManager.get().createPosition(container, 1, 11));

        // rename existing well group
        ((WellGroupImpl) savedWg1).setName("wg1_renamed");

        // add positions
        controlWellGroups.get(0).setPositions(List.of(
                PlateManager.get().createPosition(container, 0, 0),
                PlateManager.get().createPosition(container, 0, 1)));

        // delete well group
        ((PlateImpl) savedTemplate).markWellGroupForDeletion(controlWellGroups.get(1));

        long newPlateId = PlateManager.get().save(container, user, savedTemplate);
        assertEquals(savedTemplate.getRowId().intValue(), newPlateId);

        //
        // VERIFY UPDATE
        //

        // verify plate
        Plate updatedTemplate = PlateManager.get().getPlate(container, plateId);
        assertEquals("sally", updatedTemplate.getName());
        assertEquals(savedTemplate.getLSID(), updatedTemplate.getLSID());

        // verify well group rename
        WellGroup updatedWg1 = updatedTemplate.getWellGroup(savedWg1.getRowId());
        assertNotNull(updatedWg1);
        assertEquals(savedWg1.getLSID(), updatedWg1.getLSID());
        assertEquals("wg1_renamed", updatedWg1.getName());

        // verify added well group
        WellGroup updatedWg2 = updatedTemplate.getWellGroup(wg2.getRowId());
        assertNotNull(updatedWg2);

        // verify deleted well group
        List<? extends WellGroup> updatedControlWellGroups = updatedTemplate.getWellGroups(WellGroup.Type.CONTROL);
        assertEquals(1, updatedControlWellGroups.size());

        // verify added positions
        assertEquals(2, updatedControlWellGroups.getFirst().getPositions().size());

        // verify plate type information
        assertEquals(plateType.getRows().intValue(), updatedTemplate.getRows());
        assertEquals(plateType.getColumns().intValue(), updatedTemplate.getColumns());

        //
        // DELETE
        //

        PlateManager.get().deletePlate(container, user, updatedTemplate.getRowId());

        assertNull(PlateManager.get().getPlate(container, updatedTemplate.getRowId()));
    }

    @Test
    public void testCreateAndSavePlate() throws Exception
    {
        // Act
        Plate plate = createPlate(PLATE_TYPE_96_WELLS, "testCreateAndSavePlate plate", null, null);

        // Assert
        assertTrue("Expected plate to have been persisted and provided with a rowId", plate.getRowId() > 0);
        assertNotNull("Expected plate to have been persisted and provided with a plateId", plate.getPlateId());

        // verify access via plate ID
        Plate savedPlate = PlateManager.get().getPlate(container, plate.getPlateId());
        assertNotNull("Expected plate to be accessible via it's plate ID", savedPlate);
        assertEquals("Plate retrieved by plate ID doesn't match the original plate.", savedPlate.getRowId(), plate.getRowId());

        // verify container filter access
        savedPlate = PlateManager.get().getPlate(ContainerManager.getSharedContainer(), plate.getRowId());
        assertNull("Saved plate should not exist in the shared container", savedPlate);

        savedPlate = PlateManager.get().getPlate(ContainerFilter.Type.CurrentAndSubfolders.create(ContainerManager.getSharedContainer(), user), plate.getRowId());
        assertEquals("Expected plate to be accessible via a container filter", plate.getRowId(), savedPlate.getRowId());
    }

    @Test
    public void testAccessPlateByIdentifiers() throws Exception
    {
        // Arrange
        PlateType plateType = PLATE_TYPE_96_WELLS;
        PlateSetImpl plateSetImpl = new PlateSetImpl();
        plateSetImpl.setName("testAccessPlateByIdentifiersPlateSet");
        ContainerFilter cf = ContainerFilter.Type.CurrentAndSubfolders.create(ContainerManager.getSharedContainer(), user);
        var plateData = List.of(
            new PlateManager.PlateData("testAccessPlateByIdentifiersFirst", plateType.getRowId(), null, null, null),
            new PlateManager.PlateData(null, plateType.getRowId(), null, null, null),
            new PlateManager.PlateData(null, plateType.getRowId(), null, null, null)
        );

        // Act
        PlateSet plateSet = createPlateSet(plateSetImpl, plateData, null);

        // Assert
        assertTrue("Expected plateSet to have been persisted and provided with a rowId", plateSet.getRowId() > 0);
        List<? extends Plate> plates = plateSet.getPlates();
        assertEquals("Expected plateSet to have 3 plates", 3, plates.size());

        // verify access via plate rowId
        assertNotNull("Expected plate to be accessible via it's rowId", PlateManager.get().getPlate(cf, plateSet.getRowId(), plates.get(0).getRowId()));
        assertNotNull("Expected plate to be accessible via it's rowId", PlateManager.get().getPlate(cf, plateSet.getRowId(), plates.get(1).getRowId()));
        assertNotNull("Expected plate to be accessible via it's rowId", PlateManager.get().getPlate(cf, plateSet.getRowId(), plates.get(2).getRowId()));

        // verify access via plate ID
        assertNotNull("Expected plate to be accessible via it's plate ID", PlateManager.get().getPlate(cf, plateSet.getRowId(), plates.get(0).getPlateId()));
        assertNotNull("Expected plate to be accessible via it's plate ID", PlateManager.get().getPlate(cf, plateSet.getRowId(), plates.get(1).getPlateId()));
        assertNotNull("Expected plate to be accessible via it's plate ID", PlateManager.get().getPlate(cf, plateSet.getRowId(), plates.get(2).getPlateId()));

        // verify access via plate name
        assertNotNull("Expected plate to be accessible via it's name", PlateManager.get().getPlate(cf, plateSet.getRowId(), "testAccessPlateByIdentifiersFirst"));
        // verify error when trying to access non-existing plate name
        try
        {
            PlateManager.get().getPlate(cf, plateSet.getRowId(), "testAccessPlateByIdentifiersBogus");
            fail("Expected a validation error when accessing plates by non-existing name");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("Expected validation exception", "The plate identifier \"testAccessPlateByIdentifiersBogus\" does not match any plate in the plate set \"testAccessPlateByIdentifiersPlateSet\".", e.getMessage());
        }
    }

    @Test
    public void testCreatePlateTemplates() throws Exception
    {
        // Verify plate service assumptions about plate templates
        Plate template = createPlateTemplate(PLATE_TYPE_384_WELLS, "my plate template", null);

        // Assert
        assertNotNull("Expected plate template to be persisted", template);
        assertTrue("Expected saved plate to have the template field set to true", template.isTemplate());

        // Create an additional plate to ensure getPlateTemplates() does not include it
        createPlate(PLATE_TYPE_96_WELLS);

        // Verify only plate templates are returned
        List<? extends Plate> templates = PlateManager.get().getPlateTemplates(container);
        assertFalse("Expected there to be a plate template", templates.isEmpty());
        for (Plate t : templates)
            assertTrue("Expected saved plate to have the template field set to true", t.isTemplate());
    }

    @Test
    public void testCreatePlateMetadata() throws Exception
    {
        Plate plate = createPlateTemplate(PLATE_TYPE_384_WELLS, "new plate with metadata", null);
        long plateId = plate.getRowId();

        // Assert
        assertTrue("Expected saved plateId to be returned", plateId != 0);

        List<PlateCustomField> fields = PlateManager.get().getPlateMetadataFields(container, user);
        List<String> metadataFields = List.of(
            "Amount",
            "AmountUnits",
            PlateMetadataFields.barcode.name(),
            "Concentration",
            "ConcentrationUnits",
            PlateMetadataFields.description.name(),
            PlateMetadataFields.negativeControl.name(),
            PlateMetadataFields.opacity.name()
        );

        // Verify returned sorted by name should include built in as well as custom created fields
        assertEquals("Expected plate custom fields", metadataFields.size(), fields.size());

        for (int i = 0; i < metadataFields.size(); i++)
        {
            String fieldName = metadataFields.get(i);
            assertEquals(String.format("Expected %s custom field", fieldName), fieldName, fields.get(i).getName());
        }

        // assign custom fields to the plate
        assertEquals("Expected custom fields to be added to the plate", 11, PlateManager.get().addFields(container, user, plateId, fields).size());

        // remove amount and amountUnits metadata fields
        fields = PlateManager.get().removeFields(container, user, plateId, List.of(fields.get(0), fields.get(1)));
        assertEquals("Unexpected number of custom fields", 9, fields.size());
        assertEquals("Expected Concentration custom field", "Concentration", fields.get(4).getName());
        assertEquals("Expected ConcentrationUnits custom field", "ConcentrationUnits", fields.get(5).getName());

        // select wells
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("PlateId"), plateId);
        filter.addCondition(FieldKey.fromParts("Row"), 0);
        List<WellBean> wells = new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), filter, new Sort("Col")).getArrayList(WellBean.class);

        assertEquals("Expected 24 wells to be returned", 24, wells.size());

        // update
        // add metadata to 2 rows
        List<Map<String, Object>> rows = List.of(
            CaseInsensitiveHashMap.of(
                "rowid", wells.get(0).getRowId(),
                "concentration", 1.25,
                PlateMetadataFields.negativeControl.name(), 5.25
            ),
            CaseInsensitiveHashMap.of(
                "rowid", wells.get(1).getRowId(),
                "concentration", 2.25,
                PlateMetadataFields.negativeControl.name(), 6.25
            )
        );
        updateWells(rows);

        FieldKey fkConcentration = FieldKey.fromParts("concentration");
        FieldKey fkNegativeControl = FieldKey.fromParts(PlateMetadataFields.negativeControl.name());
        TableInfo wellTable = getWellTable();
        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(wellTable, List.of(fkConcentration, fkNegativeControl));

        // verify plate metadata property updates
        try (Results r = QueryService.get().getSelectBuilder(wellTable).columns(columns.values()).filter(filter).sort(new Sort("Col")).select())
        {
            int row = 0;
            while (r.next())
            {
                if (row == 0)
                {
                    assertEquals(1.25, r.getDouble(fkConcentration), 0);
                    assertEquals(5.25, r.getDouble(fkNegativeControl), 0);
                }
                else if (row == 1)
                {
                    assertEquals(2.25, r.getDouble(fkConcentration), 0);
                    assertEquals(6.25, r.getDouble(fkNegativeControl), 0);
                }
                else
                {
                    // the remainder should be null
                    assertEquals(0, r.getDouble(fkConcentration), 0);
                    assertEquals(0, r.getDouble(fkNegativeControl), 0);
                }
                row++;
            }
        }
    }

    @Test
    public void testCreateAndSavePlateWithData() throws Exception
    {
        // Act
        List<Map<String, Object>> rows = List.of(
            wellWithMetdata(createWellRow("A1", null, null), 2.25, "B1234"),
            wellWithMetdata(createWellRow("A2", null, null), 1.25, "B5678")
        );

        Plate plate = createPlate(PLATE_TYPE_96_WELLS, "hit selection plate", null, rows);
        assertEquals("Unexpected number of plate custom fields", 6, plate.getCustomFields().size());

        TableInfo wellTable = getWellTable();
        FieldKey fkConcentration = FieldKey.fromParts("concentration");
        FieldKey fkBarcode = FieldKey.fromParts(PlateMetadataFields.barcode.name());
        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(wellTable, List.of(fkConcentration, fkBarcode));

        // verify that well data was added
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());
        filter.addCondition(FieldKey.fromParts("Row"), 0);
        try (Results r = QueryService.get().getSelectBuilder(wellTable).columns(columns.values()).filter(filter).sort(new Sort("Col")).select())
        {
            int row = 0;
            while (r.next())
            {
                if (row == 0)
                {
                    assertEquals(2.25, r.getDouble(fkConcentration), 0);
                    assertEquals("B1234", r.getString(fkBarcode));
                }
                else if (row == 1)
                {
                    assertEquals(1.25, r.getDouble(fkConcentration), 0);
                    assertEquals("B5678", r.getString(fkBarcode));
                }
                else
                {
                    // the remainder should be null
                    assertEquals(0, r.getDouble(fkConcentration), 0);
                    assertNull(r.getString(fkBarcode));
                }
                row++;
            }
        }
    }

    @Test
    public void testGetWellSampleData() throws Exception
    {
        // Act
        List<Long> sampleIds = List.of(0L, 3L, 5L, 8L, 10L, 11L, 12L, 13L, 15L, 17L, 19L);
        Pair<Integer, List<Map<String, Object>>> wellSampleDataFilledFull = PlateManager.get().getWellSampleData(container, sampleIds, 2, 3, 0, null);
        Pair<Integer, List<Map<String, Object>>> wellSampleDataFilledPartial = PlateManager.get().getWellSampleData(container, sampleIds, 2, 3, 6, null);

        // Assert
        assertEquals(6, wellSampleDataFilledFull.first, 0);
        List<String> wellLocations = List.of("A1", "A2", "A3", "B1", "B2", "B3");
        for (int i = 0; i < wellSampleDataFilledFull.second.size(); i++)
        {
            Map<String, Object> well = wellSampleDataFilledFull.second.get(i);
            assertEquals(well.get("sampleId"), sampleIds.get(i));
            assertEquals(well.get("wellLocation"), wellLocations.get(i));
        }

        assertEquals(11, wellSampleDataFilledPartial.first, 0);
        for (int i = 0; i < wellSampleDataFilledPartial.second.size(); i++)
        {
            Map<String, Object> well = wellSampleDataFilledPartial.second.get(i);
            assertEquals(well.get("sampleId"), sampleIds.get(i + 6));
            assertEquals(well.get("wellLocation"), wellLocations.get(i));
        }

        // Act
        try
        {
            PlateManager.get().getWellSampleData(container, Collections.emptyList(), 2, 3, 0, null);
        }
        // Assert
        catch (ValidationException e)
        {
            assertEquals("Expected validation exception", "No samples are in the current selection.", e.getMessage());
        }
    }

    @Test
    public void testGetInstrumentInstructions() throws Exception
    {
        // Arrange
        List<ExpMaterial> samples = createSamples(2);
        ExpMaterial sample1 = samples.get(0);
        ExpMaterial sample2 = samples.get(1);

        List<Map<String, Object>> rows = List.of(
            wellWithMetdata(createWellRow("A1", "SAMPLE", sample1.getRowId()), 2.25, "B1234"),
            wellWithMetdata(createWellRow("A2", "SAMPLE", sample2.getRowId()), 1.25, "B5678")
        );
        Plate plate = createPlate(PLATE_TYPE_96_WELLS, "myPlate", null, rows);
        PlateSet plateSet = plate.getPlateSet();
        assertNotNull(plateSet);

        // Act
        List<FieldKey> includedMetadataCols = PlateManager.get().getMetadataColumns(
            plateSet,
            container,
            user,
            ContainerFilter.Type.CurrentAndSubfolders.create(container, user)
        );
        List<Object[]> result = PlateManager.get().getInstrumentInstructions(plateSet.getRowId(), includedMetadataCols, container, user);

        // Assert
        Object[] valuesRow1 = new Object[]{"myPlate", plate.getBarcode(), "A1", 96, sample1.getName(), "SAMPLE", null, null, "B1234", "2.25"};
        assertArrayEquals(valuesRow1, result.get(0));

        Object[] valuesRow2 = new Object[]{"myPlate", plate.getBarcode(), "A2", 96, sample2.getName(), "SAMPLE", null, null, "B5678", "1.25"};
        assertArrayEquals(valuesRow2, result.get(1));
    }

    private void assertWorklistThrows(String message, Long sourceRowId, Long destinationRowId, List<FieldKey> sourceIncludedMetadataCols, List<FieldKey> destinationIncludedMetadataCols)
    {
        try
        {
            PlateManager.get().getWorklist(sourceRowId, destinationRowId, sourceIncludedMetadataCols, destinationIncludedMetadataCols, container, user);
        }
        catch (Throwable t)
        {
            assertEquals("Worklist generation did not throw the expected error.", message, t.getMessage());
            return;
        }

        fail(String.format("Worklist generation failed to throw. Expected \"%s\".", message));
    }

    @Test
    public void testGetWorklist() throws Exception
    {
        // Arrange
        ContainerFilter cf = ContainerFilter.Type.CurrentAndSubfolders.create(container, user);

        List<ExpMaterial> samples = createSamples(2);
        ExpMaterial sample1 = samples.get(0);
        ExpMaterial sample2 = samples.get(1);

        List<Map<String, Object>> sourceWells = List.of(
            wellWithMetdata(createWellRow("A1", "SAMPLE", sample1.getRowId()), 2.25, "B1234"),
            wellWithMetdata(createWellRow("A2", "SAMPLE", sample2.getRowId()), 1.25, "B5678")
        );
        Plate plateSource = createPlate(PLATE_TYPE_96_WELLS, "myPlate1-1", null, sourceWells);

        List<Map<String, Object>> destinationWells = List.of(
            createWellRow("A1", "SAMPLE", sample2.getRowId()),
            createWellRow("A2", "SAMPLE", sample1.getRowId()),
            createWellRow("A3", "SAMPLE", sample2.getRowId())
        );
        Plate plateDestination = createPlate(PLATE_TYPE_96_WELLS, "myPlate2-1", null, destinationWells);

        // Act
        List<FieldKey> sourceIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateSource.getPlateSet(), container, user, cf);
        List<FieldKey> destinationIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateDestination.getPlateSet(), container, user, cf);
        List<Object[]> plateDataRows = PlateManager.get().getWorklist(plateSource.getPlateSet().getRowId(), plateDestination.getPlateSet().getRowId(), sourceIncludedMetadataCols, destinationIncludedMetadataCols, container, user);

        // Assert
        Object[] valuesRow1 = new Object[]{"myPlate1-1", plateSource.getBarcode(), "A1", 96, sample1.getName(), "SAMPLE", null, null, "B1234", "2.25", "myPlate2-1", plateDestination.getBarcode(), "A2", 96, "SAMPLE", null, null};
        assertArrayEquals(valuesRow1, plateDataRows.get(0));

        Object[] valuesRow2 = new Object[]{"myPlate1-1", plateSource.getBarcode(),"A2", 96, sample2.getName(), "SAMPLE", null, null, "B5678", "1.25", "myPlate2-1", plateDestination.getBarcode(), "A1", 96, "SAMPLE", null, null};
        assertArrayEquals(valuesRow2, plateDataRows.get(1));

        Object[] valuesRow3 = new Object[]{"myPlate1-1", plateSource.getBarcode(),"A2", 96, sample2.getName(), "SAMPLE", null, null, "B5678", "1.25", "myPlate2-1", plateDestination.getBarcode(), "A3", 96, "SAMPLE", null, null};
        assertArrayEquals(valuesRow3, plateDataRows.get(2));
    }

    @Test
    public void testGetWorklistWithEmptyDestinations() throws Exception
    {
        // Arrange
        ContainerFilter cf = ContainerFilter.Type.CurrentAndSubfolders.create(container, user);

        List<ExpMaterial> samples = createSamples(2);
        ExpMaterial sample1 = samples.get(0);
        ExpMaterial sample2 = samples.get(1);

        List<Map<String, Object>> rows1 = List.of(
            wellWithMetdata(createWellRow("A1", "SAMPLE", sample1.getRowId()), 2.25, "B1234"),
            wellWithMetdata(createWellRow("A2", "SAMPLE", sample2.getRowId()), 1.25, "B5678")
        );
        Plate plateSource = createPlate(PLATE_TYPE_96_WELLS, "myPlate1-2", null, rows1);

        List<Map<String, Object>> rows2 = List.of(createWellRow("A1", "SAMPLE", sample2.getRowId()));
        Plate plateDestination = createPlate(PLATE_TYPE_96_WELLS, "myPlate2-2", null, rows2);

        // Act
        List<FieldKey> sourceIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateSource.getPlateSet(), container, user, cf);
        List<FieldKey> destinationIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateDestination.getPlateSet(), container, user, cf);
        List<Object[]> plateDataRows = PlateManager.get().getWorklist(plateSource.getPlateSet().getRowId(), plateDestination.getPlateSet().getRowId(), sourceIncludedMetadataCols, destinationIncludedMetadataCols, container, user);

        // Assert
        Object[] valuesRow1 = new Object[]{"myPlate1-2", plateSource.getBarcode(), "A1", 96, sample1.getName(), "SAMPLE", null, null, "B1234", "2.25", null, null, null, null, null, null, null};
        assertArrayEquals(valuesRow1, plateDataRows.get(0));

        Object[] valuesRow2 = new Object[]{"myPlate1-2", plateSource.getBarcode(),"A2", 96, sample2.getName(), "SAMPLE", null, null, "B5678", "1.25", "myPlate2-2", plateDestination.getBarcode(), "A1", 96, "SAMPLE", null, null};
        assertArrayEquals(valuesRow2, plateDataRows.get(1));
    }

    @Test
    public void testGetWorklistSingleSampleManyToMany() throws Exception
    {
        // Arrange
        ContainerFilter cf = ContainerFilter.Type.CurrentAndSubfolders.create(container, user);
        ExpMaterial sample = createSamples(1).getFirst();

        List<Map<String, Object>> rows1 = List.of(
            wellWithMetdata(createWellRow("A1", "SAMPLE", sample.getRowId()), 2.25, "B1234"),
            wellWithMetdata(createWellRow("A2", "SAMPLE", sample.getRowId()), 1.25, "B5678"),
            wellWithMetdata(createWellRow("A3", "SAMPLE", sample.getRowId()), 1.00, "B910")
        );
        Plate plateSource = createPlate(PLATE_TYPE_96_WELLS, "myPlate1-3", null, rows1);

        List<Map<String, Object>> rows2 = List.of(
            createWellRow("A1", "SAMPLE", sample.getRowId()),
            createWellRow("A2", "SAMPLE", sample.getRowId())
        );
        Plate plateDestination = createPlate(PLATE_TYPE_96_WELLS, "myPlate2-3", null, rows2);

        // Act
        List<FieldKey> sourceIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateSource.getPlateSet(), container, user, cf);
        List<FieldKey> destinationIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateDestination.getPlateSet(), container, user, cf);

        // Assert
        assertWorklistThrows("Many-to-many single-sample operation detected. See sample(s): " + sample.getName(), plateSource.getPlateSet().getRowId(), plateDestination.getPlateSet().getRowId(), sourceIncludedMetadataCols, destinationIncludedMetadataCols);
    }

    @Test
    public void testGetWorklistSingleSampleOneToOne() throws Exception
    {
        // Arrange
        ContainerFilter cf = ContainerFilter.Type.CurrentAndSubfolders.create(container, user);
        ExpMaterial sample = createSamples(3).getFirst();

        List<Map<String, Object>> rows1 = List.of(
            wellWithMetdata(createWellRow("A1", "SAMPLE", sample.getRowId()), 2.25, "B1234"),
            wellWithMetdata(createWellRow("A2", "SAMPLE", sample.getRowId()), 1.25, "B5678"),
            wellWithMetdata(createWellRow("A3", "SAMPLE", sample.getRowId()), 1.00, "B910")
        );
        Plate plateSource = createPlate(PLATE_TYPE_96_WELLS, "myPlate1-4", null, rows1);

        List<Map<String, Object>> rows2 = List.of(
            createWellRow("A2", "SAMPLE", sample.getRowId()),
            createWellRow("A3", "SAMPLE", sample.getRowId()),
            createWellRow("A4", "SAMPLE", sample.getRowId())
        );
        Plate plateDestination = createPlate(PLATE_TYPE_96_WELLS, "myPlate2-4", null, rows2);

        // Act
        List<FieldKey> sourceIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateSource.getPlateSet(), container, user, cf);
        List<FieldKey> destinationIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateDestination.getPlateSet(), container, user, cf);
        List<Object[]> plateDataRows = PlateManager.get().getWorklist(plateSource.getPlateSet().getRowId(), plateDestination.getPlateSet().getRowId(), sourceIncludedMetadataCols, destinationIncludedMetadataCols, container, user);

        // Assert
        Object[] valuesRow1 = new Object[]{"myPlate1-4", plateSource.getBarcode(), "A1", 96, sample.getName(), "SAMPLE", null, null, "B1234", "2.25", "myPlate2-4", plateDestination.getBarcode(), "A2", 96, "SAMPLE", null, null};
        assertArrayEquals(valuesRow1, plateDataRows.get(0));

        Object[] valuesRow2 = new Object[]{"myPlate1-4", plateSource.getBarcode(),"A2", 96, sample.getName(), "SAMPLE", null, null, "B5678", "1.25", "myPlate2-4", plateDestination.getBarcode(), "A3", 96, "SAMPLE", null, null};
        assertArrayEquals(valuesRow2, plateDataRows.get(1));

        Object[] valuesRow3 = new Object[]{"myPlate1-4", plateSource.getBarcode(),"A3", 96, sample.getName(), "SAMPLE", null, null, "B910", "1.0", "myPlate2-4", plateDestination.getBarcode(), "A4", 96, "SAMPLE", null, null};
        assertArrayEquals(valuesRow3, plateDataRows.get(2));
    }

    @Test
    public void testGetWorklistSingleSampleOneToMany() throws Exception
    {
        // Arrange
        ContainerFilter cf = ContainerFilter.Type.CurrentAndSubfolders.create(container, user);
        ExpMaterial sample = createSamples(3).getFirst();

        List<Map<String, Object>> rows1 = List.of(
            wellWithMetdata(createWellRow("A1", "SAMPLE", sample.getRowId()), 2.25, "B1234")
        );
        Plate plateSource = createPlate(PLATE_TYPE_96_WELLS, "myPlate1-5", null, rows1);

        List<Map<String, Object>> rows2 = List.of(
            createWellRow("A2", "SAMPLE", sample.getRowId()),
            createWellRow("A3", "SAMPLE", sample.getRowId()),
            createWellRow("A4", "SAMPLE", sample.getRowId())
        );
        Plate plateDestination = createPlate(PLATE_TYPE_96_WELLS, "myPlate2-5", null, rows2);

        // Act
        List<FieldKey> sourceIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateSource.getPlateSet(), container, user, cf);
        List<FieldKey> destinationIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateDestination.getPlateSet(), container, user, cf);
        List<Object[]> plateDataRows = PlateManager.get().getWorklist(plateSource.getPlateSet().getRowId(), plateDestination.getPlateSet().getRowId(), sourceIncludedMetadataCols, destinationIncludedMetadataCols, container, user);

        // Assert
        Object[] valuesRow1 = new Object[]{"myPlate1-5", plateSource.getBarcode(), "A1", 96, sample.getName(), "SAMPLE", null, null, "B1234", "2.25", "myPlate2-5", plateDestination.getBarcode(), "A2", 96, "SAMPLE", null, null};
        assertArrayEquals(valuesRow1, plateDataRows.get(0));

        Object[] valuesRow2 = new Object[]{"myPlate1-5", plateSource.getBarcode(),"A1", 96, sample.getName(), "SAMPLE", null, null, "B1234", "2.25", "myPlate2-5", plateDestination.getBarcode(), "A3", 96, "SAMPLE", null, null};
        assertArrayEquals(valuesRow2, plateDataRows.get(1));

        Object[] valuesRow3 = new Object[]{"myPlate1-5", plateSource.getBarcode(),"A1", 96, sample.getName(), "SAMPLE", null, null, "B1234", "2.25", "myPlate2-5", plateDestination.getBarcode(), "A4", 96, "SAMPLE", null, null};
        assertArrayEquals(valuesRow3, plateDataRows.get(2));
    }

    private void assertReformatThrows(String message, ReformatOptions options)
    {
        try
        {
            PlateManager.get().reformat(container, user, options);
        }
        catch (Throwable t)
        {
            assertEquals("Plate reformat did not throw the expected error.", message, t.getMessage());
            return;
        }

        fail(String.format("Plate reformat failed to throw. Expected \"%s\".", message));
    }

    private ReformatOptions defaultOptions()
    {
        return new ReformatOptions()
            .setOperation(ReformatOptions.ReformatOperation.stamp)
            .setTargetPlateSet(new ReformatOptions.TargetPlateSet().setRowId(EMPTY_PLATE_SET_ID));
    }

    @Test
    public void testReformatTargetPlateSet()
    {
        assertReformatThrows("Reformat options are required.", null);
        assertReformatThrows("An \"operation\" must be specified.", new ReformatOptions());

        assertReformatThrows("A \"targetPlateSet\" must be specified.", defaultOptions().setTargetPlateSet(null));
        assertReformatThrows(
            "Either a \"rowId\" or a \"type\" must be specified for \"targetPlateSet\".",
            defaultOptions().setTargetPlateSet(new ReformatOptions.TargetPlateSet())
        );
        assertReformatThrows(
            "Either a \"rowId\" or a \"type\" must be specified for \"targetPlateSet\".",
            defaultOptions().setTargetPlateSet(new ReformatOptions.TargetPlateSet().setRowId(null))
        );
        assertReformatThrows(
            "Either a \"rowId\" or a \"type\" must be specified for \"targetPlateSet\".",
            defaultOptions().setTargetPlateSet(new ReformatOptions.TargetPlateSet().setRowId(0L))
        );
        assertReformatThrows(
            "Either a \"rowId\" or a \"type\" can be specified for \"targetPlateSet\" but not both.",
            defaultOptions().setTargetPlateSet(new ReformatOptions.TargetPlateSet().setRowId(1L).setType(PlateSetType.assay))
        );

        PlateSet archivedPlateSet = PlateManager.get().getPlateSet(container, ARCHIVED_PLATE_SET_ID);
        assertNotNull(archivedPlateSet);

        assertReformatThrows(
            String.format("Plate Set \"%s\" is archived and cannot be modified.", archivedPlateSet.getName()),
            defaultOptions().setTargetPlateSet(new ReformatOptions.TargetPlateSet().setRowId(ARCHIVED_PLATE_SET_ID))
        );

        PlateSet fullPlateSet = PlateManager.get().getPlateSet(container, FULL_PLATE_SET_ID);
        assertNotNull(fullPlateSet);

        assertReformatThrows(
            String.format("Plate Set \"%s\" is full and cannot include additional plates.", fullPlateSet.getName()),
            defaultOptions().setTargetPlateSet(new ReformatOptions.TargetPlateSet().setRowId(FULL_PLATE_SET_ID))
        );
    }

    @Test
    public void testReformatSourcePlates() throws Exception
    {
        assertReformatThrows(
            "Either \"plateRowIds\" or \"plateSelectionKey\" can be specified but not both.",
            defaultOptions().setPlateRowIds(List.of(1234L)).setPlateSelectionKey("1234")
        );
        assertReformatThrows(
            "Either \"plateRowIds\" or \"plateSelectionKey\" must be specified for this operation.",
            defaultOptions().setPlateRowIds(null).setPlateSelectionKey(" ")
        );
        assertReformatThrows("No source plates are specified.", defaultOptions().setPlateSelectionKey("1234"));

        List<Long> withNulls = new ArrayList<>();
        withNulls.add(null);
        assertReformatThrows("An invalid null plate row id was specified.", defaultOptions().setPlateRowIds(withNulls));
        assertReformatThrows("An invalid plate row id (-10) was specified.", defaultOptions().setPlateRowIds(List.of(-10L)));

        // Create plates in two different plate sets and attempt to reformat them together
        Plate p1 = createPlate(PLATE_TYPE_12_WELLS);
        Plate p2 = createPlate(PLATE_TYPE_12_WELLS);
        assertReformatThrows("All source plates must be from the same plate set.", defaultOptions().setPlateRowIds(List.of(p1.getRowId(), p2.getRowId())));
    }

    @Test
    public void testReformatQuadrant() throws Exception
    {
        // Arrange
        List<Map<String, Object>> sourcePlate1Data = List.of(
            CaseInsensitiveHashMap.of("wellLocation", "A1", PlateMetadataFields.barcode.name(), "BC-A1"),
            CaseInsensitiveHashMap.of("wellLocation", "A12", PlateMetadataFields.barcode.name(), "BC-A12"),
            CaseInsensitiveHashMap.of("wellLocation", "H1", PlateMetadataFields.barcode.name(), "BC-H1"),
            CaseInsensitiveHashMap.of("wellLocation", "H12", PlateMetadataFields.barcode.name(), "BC-H12")
        );
        List<Map<String, Object>> sourcePlate2Data = List.of(
            CaseInsensitiveHashMap.of("wellLocation", "B2", PlateMetadataFields.barcode.name(), "BC-B2"),
            CaseInsensitiveHashMap.of("wellLocation", "B11", PlateMetadataFields.barcode.name(), "BC-B11"),
            CaseInsensitiveHashMap.of("wellLocation", "G2", PlateMetadataFields.barcode.name(), "BC-G2"),
            CaseInsensitiveHashMap.of("wellLocation", "G11", PlateMetadataFields.barcode.name(), "BC-G11")
        );
        List<Map<String, Object>> sourcePlate3Data = List.of(
            CaseInsensitiveHashMap.of("wellLocation", "C3", PlateMetadataFields.barcode.name(), "BC-C3"),
            CaseInsensitiveHashMap.of("wellLocation", "C10", PlateMetadataFields.barcode.name(), "BC-C10"),
            CaseInsensitiveHashMap.of("wellLocation", "F3", PlateMetadataFields.barcode.name(), "BC-F3"),
            CaseInsensitiveHashMap.of("wellLocation", "F10", PlateMetadataFields.barcode.name(), "BC-F10")
        );
        var sourcePlateSet = createPlateSet(new PlateSetImpl(), null, null);
        var sourcePlate1 = createPlate(PLATE_TYPE_96_WELLS, "96-well source plate 1", sourcePlateSet.getRowId(), sourcePlate1Data);
        var sourcePlate2 = createPlate(PLATE_TYPE_96_WELLS, "96-well source plate 2", sourcePlateSet.getRowId(), sourcePlate2Data);
        var sourcePlate3 = createPlate(PLATE_TYPE_96_WELLS, "96-well source plate 3", sourcePlateSet.getRowId(), sourcePlate3Data);

        var options = new ReformatOptions()
                .setOperation(ReformatOptions.ReformatOperation.quadrant)
                .setPlateRowIds(List.of(sourcePlate1.getRowId(), sourcePlate2.getRowId(), sourcePlate3.getRowId()))
                .setTargetPlateSet(new ReformatOptions.TargetPlateSet().setType(PlateSetType.assay))
                .setTargetPlateSource(new ReformatOptions.TargetPlateSource(PLATE_TYPE_384_WELLS))
                .setPreview(true);

        // Act (preview)
        var result = PlateManager.get().reformat(container, user, options);

        // Assert
        assertNotNull(result.previewData());
        assertEquals("Expected quadrant operation on 3 plates to generate 1 plate.", 1, result.previewData().size());

        var previewPlate = result.previewData().getFirst();
        var wellData = previewPlate.data();
        assertEquals("Expected 12 wells to have data", 12, wellData.size());

        var expectedData = new LinkedHashMap<String, String>();
        expectedData.put("A1", "BC-A1");
        expectedData.put("A12", "BC-A12");
        expectedData.put("B14", "BC-B2");
        expectedData.put("B23", "BC-B11");
        expectedData.put("G14", "BC-G2");
        expectedData.put("G23", "BC-G11");
        expectedData.put("H1", "BC-H1");
        expectedData.put("H12", "BC-H12");
        expectedData.put("K3", "BC-C3");
        expectedData.put("K10", "BC-C10");
        expectedData.put("N3", "BC-F3");
        expectedData.put("N10", "BC-F10");

        int i = 0;
        for (var entry : expectedData.entrySet())
        {
            assertEquals(entry.getKey(), wellData.get(i).get("WellLocation"));
            assertEquals(entry.getValue(), wellData.get(i).get(PlateMetadataFields.barcode.name()));
            i++;
        }

        // Act (saved)
        result = PlateManager.get().reformat(container, user, options.setPreview(false));

        // Assert
        assertNull(result.previewData());
        assertTrue("Expected a new plate set to be created", result.plateSetRowId() > 0);
        assertEquals(1, result.plateRowIds().size());

        var newPlate = PlateManager.get().getPlate(container, result.plateRowIds().getFirst());
        assertNotNull(newPlate);
        assertEquals(PLATE_TYPE_384_WELLS, newPlate.getPlateType());

        try (var r = getPlateWellResults(newPlate.getRowId()))
        {
            while (r.next())
            {
                var wellPosition = r.getString(FieldKey.fromParts("position"));
                if (expectedData.containsKey(wellPosition))
                {
                    var expectedBarcode = expectedData.get(wellPosition);
                    assertEquals(expectedBarcode, r.getString(FieldKey.fromParts(PlateMetadataFields.barcode.name())));
                }
            }
        }
    }

    @Test
    public void testReformatReverseQuadrant() throws Exception
    {
        // Arrange
        List<Map<String, Object>> sourcePlateData = List.of(
            CaseInsensitiveHashMap.of("wellLocation", "A1", PlateMetadataFields.barcode.name(), "BC-A1"),
            CaseInsensitiveHashMap.of("wellLocation", "H12", PlateMetadataFields.barcode.name(), "BC-H12"),
            CaseInsensitiveHashMap.of("wellLocation", "H13", PlateMetadataFields.barcode.name(), "BC-H13"),
            CaseInsensitiveHashMap.of("wellLocation", "I12", PlateMetadataFields.barcode.name(), "BC-I12"),
            CaseInsensitiveHashMap.of("wellLocation", "I13", PlateMetadataFields.barcode.name(), "BC-I13"),
            CaseInsensitiveHashMap.of("wellLocation", "P24", PlateMetadataFields.barcode.name(), "BC-P24")
        );
        Plate sourcePlate = createPlate(PLATE_TYPE_384_WELLS, "384-well source plate", null, sourcePlateData);
        Long targetPlateSetId = sourcePlate.getPlateSet().getRowId();

        ReformatOptions options = new ReformatOptions()
            .setOperation(ReformatOptions.ReformatOperation.reverseQuadrant)
            .setPlateRowIds(List.of(sourcePlate.getRowId()))
            .setTargetPlateSet(new ReformatOptions.TargetPlateSet().setRowId(targetPlateSetId))
            .setTargetPlateSource(new ReformatOptions.TargetPlateSource(PLATE_TYPE_96_WELLS))
            .setPreview(true);

        // Act (preview)
        PlateManager.ReformatResult result = PlateManager.get().reformat(container, user, options);

        // Assert
        assertNotNull(result.previewData());
        assertEquals("Expected reverse quadrant operation on a 384-well plate to generate 4 96-well plates.", 4, result.previewData().size());

        assertEquals("BC-A1", result.previewData().get(0).data().get(0).get(PlateMetadataFields.barcode.name()));
        assertEquals("BC-H12", result.previewData().get(0).data().get(1).get(PlateMetadataFields.barcode.name()));
        assertEquals("BC-I13", result.previewData().get(3).data().get(0).get(PlateMetadataFields.barcode.name()));
        assertEquals("BC-P24", result.previewData().get(3).data().get(1).get(PlateMetadataFields.barcode.name()));

        // Act (saved)
        result = PlateManager.get().reformat(container, user, options.setPreview(false));

        // Assert
        assertNull(result.previewData());
        assertEquals("Expected target plate set to be used", targetPlateSetId, result.plateSetRowId());
        assertEquals(4, result.plateRowIds().size());

        for (int i = 0; i < result.plateRowIds().size(); i++)
        {
            var plateRowId = result.plateRowIds().get(i);
            var newPlate = PlateManager.get().getPlate(container, plateRowId);
            assertNotNull(newPlate);
            assertEquals(PLATE_TYPE_96_WELLS, newPlate.getPlateType());

            try (var r = getPlateWellResults(newPlate.getRowId()))
            {
                while (r.next())
                {
                    var barcode = r.getString(FieldKey.fromParts(PlateMetadataFields.barcode.name()));

                    if (i == 0 || i == 3)
                    {
                        var wellPosition = r.getString(FieldKey.fromParts("position"));
                        if ("A1".equalsIgnoreCase(wellPosition))
                        {
                            if (i == 0) assertEquals("BC-A1", barcode);
                            if (i == 3) assertEquals("BC-I13", barcode);
                        }
                        else if ("H12".equalsIgnoreCase(wellPosition))
                        {
                            if (i == 0) assertEquals("BC-H12", barcode);
                            if (i == 3) assertEquals("BC-P24", barcode);
                        }
                        else
                            assertNull(barcode);
                    }
                }
            }
        }
    }

    @Test
    public void testReformatCompressByColumn() throws Exception
    {
        List<Long> sampleRowIds = createSamples(6).stream().map(ExpObject::getRowId).sorted().toList();

        // Arrange
        List<Map<String, Object>> sourcePlateData = List.of(
            CaseInsensitiveHashMap.of("wellLocation", "A1", "sampleId", sampleRowIds.get(0), "type", "SAMPLE"),
            CaseInsensitiveHashMap.of("wellLocation", "H12", "sampleId", sampleRowIds.get(1), "type", "SAMPLE"),
            CaseInsensitiveHashMap.of("wellLocation", "H13", "sampleId", sampleRowIds.get(2), "type", "SAMPLE"),
            CaseInsensitiveHashMap.of("wellLocation", "I12", "sampleId", sampleRowIds.get(3), "type", "SAMPLE"),
            CaseInsensitiveHashMap.of("wellLocation", "I13", "sampleId", sampleRowIds.get(4), "type", "SAMPLE"),
            CaseInsensitiveHashMap.of("wellLocation", "P12", PlateMetadataFields.barcode.name(), "BC-P12"),
            CaseInsensitiveHashMap.of("wellLocation", "P24", "sampleId", sampleRowIds.get(5), "type", "SAMPLE")
        );
        Plate sourcePlate = createPlate(PLATE_TYPE_384_WELLS, "Column compression source plate", null, sourcePlateData);
        Long targetPlateSetId = sourcePlate.getPlateSet().getRowId();

        ReformatOptions options = new ReformatOptions()
                .setOperation(ReformatOptions.ReformatOperation.columnCompression)
                .setPlateRowIds(List.of(sourcePlate.getRowId()))
                .setTargetPlateSet(new ReformatOptions.TargetPlateSet().setRowId(targetPlateSetId))
                .setTargetPlateSource(new ReformatOptions.TargetPlateSource(PLATE_TYPE_12_WELLS))
                .setPreview(true);

        // Act (preview)
        PlateManager.ReformatResult result = PlateManager.get().reformat(container, user, options);

        // Assert
        assertNotNull(result.previewData());
        assertEquals("Expected column compress operation on a 384-well plate to generate 1 12-well plates.", 1, result.previewData().size());

        List<Map<String, Object>> plateData = result.previewData().getFirst().data();
        assertEquals("Expected well P12 to be dropped as it does not include a sample.", sourcePlateData.size() - 1, plateData.size());

        assertEquals(sampleRowIds.get(0), plateData.get(0).get("sampleId"));
        assertEquals("A1", plateData.get(0).get("wellLocation"));
        assertEquals(sampleRowIds.get(1), plateData.get(2).get("sampleId"));
        assertEquals("B1", plateData.get(2).get("wellLocation"));
        assertEquals(sampleRowIds.get(2), plateData.get(4).get("sampleId"));
        assertEquals("C1", plateData.get(4).get("wellLocation"));
        assertEquals(sampleRowIds.get(3), plateData.get(1).get("sampleId"));
        assertEquals("A2", plateData.get(1).get("wellLocation"));
        assertEquals(sampleRowIds.get(4), plateData.get(3).get("sampleId"));
        assertEquals("B2", plateData.get(3).get("wellLocation"));
        assertEquals(sampleRowIds.get(5), plateData.get(5).get("sampleId"));
        assertEquals("C2", plateData.get(5).get("wellLocation"));

        // Act (saved)
        result = PlateManager.get().reformat(container, user, options.setPreview(false));

        // Assert
        assertNull(result.previewData());
        assertEquals("Expected target plate set to be used", targetPlateSetId, result.plateSetRowId());
        assertEquals(1, result.plateRowIds().size());

        Plate newPlate = PlateManager.get().getPlate(container, result.plateRowIds().getFirst());
        assertNotNull(newPlate);
        assertEquals(PLATE_TYPE_12_WELLS, newPlate.getPlateType());

        try (var r = getPlateWellResults(newPlate.getRowId()))
        {
            while (r.next())
            {
                var sampleId = r.getInt(FieldKey.fromParts("sampleId"));
                var wellPosition = r.getString(FieldKey.fromParts("position"));

                switch (wellPosition)
                {
                    case "A1" -> assertEquals(sampleRowIds.get(0).intValue(), sampleId);
                    case "A2" -> assertEquals(sampleRowIds.get(3).intValue(), sampleId);
                    case "B1" -> assertEquals(sampleRowIds.get(1).intValue(), sampleId);
                    case "B2" -> assertEquals(sampleRowIds.get(4).intValue(), sampleId);
                    case "C1" -> assertEquals(sampleRowIds.get(2).intValue(), sampleId);
                    case "C2" -> assertEquals(sampleRowIds.get(5).intValue(), sampleId);
                    default -> assertEquals(0, sampleId);
                }
            }
        }
    }

    @Test
    public void testReformatCompressByRow() throws Exception
    {
        List<Long> sampleRowIds = createSamples(6).stream().map(ExpObject::getRowId).sorted().toList();

        // Arrange
        List<Map<String, Object>> sourcePlateData = List.of(
            CaseInsensitiveHashMap.of("wellLocation", "A1", "sampleId", sampleRowIds.get(0), "type", "SAMPLE"),
            CaseInsensitiveHashMap.of("wellLocation", "H12", "sampleId", sampleRowIds.get(1), "type", "SAMPLE"),
            CaseInsensitiveHashMap.of("wellLocation", "H13", "sampleId", sampleRowIds.get(2), "type", "SAMPLE"),
            CaseInsensitiveHashMap.of("wellLocation", "I12", "sampleId", sampleRowIds.get(3), "type", "SAMPLE"),
            CaseInsensitiveHashMap.of("wellLocation", "I13", "sampleId", sampleRowIds.get(4), "type", "SAMPLE"),
            CaseInsensitiveHashMap.of("wellLocation", "P12", PlateMetadataFields.barcode.name(), "BC-P12"),
            CaseInsensitiveHashMap.of("wellLocation", "P24", "sampleId", sampleRowIds.get(5), "type", "SAMPLE")
        );
        Plate sourcePlate = createPlate(PLATE_TYPE_384_WELLS, "Row compression source plate", null, sourcePlateData);
        Long targetPlateSetId = sourcePlate.getPlateSet().getRowId();

        ReformatOptions options = new ReformatOptions()
                .setOperation(ReformatOptions.ReformatOperation.rowCompression)
                .setPlateRowIds(List.of(sourcePlate.getRowId()))
                .setTargetPlateSet(new ReformatOptions.TargetPlateSet().setRowId(targetPlateSetId))
                .setTargetPlateSource(new ReformatOptions.TargetPlateSource(PLATE_TYPE_12_WELLS))
                .setPreview(true);

        // Act (preview)
        PlateManager.ReformatResult result = PlateManager.get().reformat(container, user, options);

        // Assert
        assertNotNull(result.previewData());
        assertEquals("Expected row compress operation on a 384-well plate to generate 1 12-well plates.", 1, result.previewData().size());

        List<Map<String, Object>> plateData = result.previewData().getFirst().data();
        assertEquals("Expected well P12 to be dropped as it does not include a sample.", sourcePlateData.size() - 1, plateData.size());

        assertEquals(sampleRowIds.get(0), plateData.get(0).get("sampleId"));
        assertEquals("A1", plateData.get(0).get("wellLocation"));
        assertEquals(sampleRowIds.get(1), plateData.get(1).get("sampleId"));
        assertEquals("A2", plateData.get(1).get("wellLocation"));
        assertEquals(sampleRowIds.get(2), plateData.get(2).get("sampleId"));
        assertEquals("A3", plateData.get(2).get("wellLocation"));
        assertEquals(sampleRowIds.get(3), plateData.get(3).get("sampleId"));
        assertEquals("A4", plateData.get(3).get("wellLocation"));
        assertEquals(sampleRowIds.get(4), plateData.get(4).get("sampleId"));
        assertEquals("B1", plateData.get(4).get("wellLocation"));
        assertEquals(sampleRowIds.get(5), plateData.get(5).get("sampleId"));
        assertEquals("B2", plateData.get(5).get("wellLocation"));

        // Act (saved)
        result = PlateManager.get().reformat(container, user, options.setPreview(false));

        // Assert
        assertNull(result.previewData());
        assertEquals("Expected target plate set to be used", targetPlateSetId, result.plateSetRowId());
        assertEquals(1, result.plateRowIds().size());

        Plate newPlate = PlateManager.get().getPlate(container, result.plateRowIds().getFirst());
        assertNotNull(newPlate);
        assertEquals(PLATE_TYPE_12_WELLS, newPlate.getPlateType());

        try (var r = getPlateWellResults(newPlate.getRowId()))
        {
            while (r.next())
            {
                var sampleId = r.getInt(FieldKey.fromParts("sampleId"));
                var wellPosition = r.getString(FieldKey.fromParts("position"));

                switch (wellPosition)
                {
                    case "A1" -> assertEquals(sampleRowIds.get(0).intValue(), sampleId);
                    case "A2" -> assertEquals(sampleRowIds.get(1).intValue(), sampleId);
                    case "A3" -> assertEquals(sampleRowIds.get(2).intValue(), sampleId);
                    case "A4" -> assertEquals(sampleRowIds.get(3).intValue(), sampleId);
                    case "B1" -> assertEquals(sampleRowIds.get(4).intValue(), sampleId);
                    case "B2" -> assertEquals(sampleRowIds.get(5).intValue(), sampleId);
                    default -> assertEquals(0, sampleId);
                }
            }
        }
    }

    private record ReformatContext(List<Plate> sourcePlates, List<Long> sampleRowIds, List<Long> controlRowIds, Long targetPlateSetId) {}

    private ReformatContext initializeReformatContext() throws Exception
    {
        List<Long> sampleRowIds = createSamples(13).stream().map(ExpObject::getRowId).sorted().toList();
        List<Long> controlRowIds = createSamples(3).stream().map(ExpObject::getRowId).sorted().toList();
        List<Plate> sourcePlates = new ArrayList<>();
        Long targetPlateSetId;

        // 12-well source plate
        {
            List<Map<String, Object>> sourcePlateData = List.of(
                CaseInsensitiveHashMap.of("wellLocation", "A1", "sampleId", sampleRowIds.get(0), "type", "SAMPLE", "wellGroup", "S1"),
                CaseInsensitiveHashMap.of("wellLocation", "A2", "sampleId", sampleRowIds.get(1), "type", "SAMPLE"),
                CaseInsensitiveHashMap.of("wellLocation", "A3", "sampleId", sampleRowIds.get(2), "type", "SAMPLE"),
                CaseInsensitiveHashMap.of("wellLocation", "A4", "sampleId", sampleRowIds.get(3), "type", "SAMPLE"),
                CaseInsensitiveHashMap.of("wellLocation", "B1", "sampleId", sampleRowIds.get(4), "type", "SAMPLE", "replicateGroup", "RB1"),
                CaseInsensitiveHashMap.of("wellLocation", "B2", "sampleId", sampleRowIds.get(4), "type", "SAMPLE", "replicateGroup", "RB1"),
                CaseInsensitiveHashMap.of("wellLocation", "B3", "sampleId", sampleRowIds.get(5), "type", "SAMPLE", "replicateGroup", "RB2"),
                CaseInsensitiveHashMap.of("wellLocation", "B4", "sampleId", sampleRowIds.get(5), "type", "SAMPLE", "replicateGroup", "RB2"),
                CaseInsensitiveHashMap.of("wellLocation", "C1", "sampleId", controlRowIds.get(0), "type", "CONTROL"),
                CaseInsensitiveHashMap.of("wellLocation", "C2", "sampleId", sampleRowIds.get(6), "type", "SAMPLE"),
                CaseInsensitiveHashMap.of("wellLocation", "C3", "sampleId", controlRowIds.get(1), "type", "CONTROL"),
                CaseInsensitiveHashMap.of("wellLocation", "C4", "sampleId", sampleRowIds.get(7), "type", "SAMPLE")
            );

            Plate plate = createPlate(PLATE_TYPE_12_WELLS, null, null, sourcePlateData);
            targetPlateSetId = plate.getPlateSet().getRowId();
            sourcePlates.add(plate);
        }

        // 96-well source plate
        {
            List<Map<String, Object>> sourcePlateData = List.of(
                CaseInsensitiveHashMap.of("wellLocation", "A1", "sampleId", sampleRowIds.get(4), "type", "SAMPLE", "replicateGroup", "RB1"),
                CaseInsensitiveHashMap.of("wellLocation", "A12", "sampleId", sampleRowIds.get(0), "type", "SAMPLE", "wellGroup", "S1"),
                CaseInsensitiveHashMap.of("wellLocation", "D6", "sampleId", sampleRowIds.get(8), "type", "SAMPLE"),
                CaseInsensitiveHashMap.of("wellLocation", "E6", "sampleId", sampleRowIds.get(9), "type", "SAMPLE"),
                CaseInsensitiveHashMap.of("wellLocation", "H1", "sampleId", controlRowIds.get(2), "type", "CONTROL"),
                CaseInsensitiveHashMap.of("wellLocation", "H2", "sampleId", sampleRowIds.get(10), "type", "SAMPLE"),
                CaseInsensitiveHashMap.of("wellLocation", "H3", "sampleId", sampleRowIds.get(11), "type", "SAMPLE"),
                CaseInsensitiveHashMap.of("wellLocation", "H4", "sampleId", sampleRowIds.get(12), "type", "SAMPLE"),
                CaseInsensitiveHashMap.of("wellLocation", "H12", "sampleId", sampleRowIds.get(5), "type", "SAMPLE", "replicateGroup", "RB2")
            );
            sourcePlates.add(createPlate(PLATE_TYPE_96_WELLS, null, targetPlateSetId, sourcePlateData));
        }

        return new ReformatContext(sourcePlates, sampleRowIds, controlRowIds, targetPlateSetId);
    }

    @Test
    public void testReformatArrayByColumn() throws Exception
    {
        // Arrange
        ReformatContext context = initializeReformatContext();

        ReformatOptions options = new ReformatOptions()
            .setOperation(ReformatOptions.ReformatOperation.arrayByColumn)
            .setPlateRowIds(context.sourcePlates.stream().map(Plate::getRowId).toList())
            .setTargetPlateSet(new ReformatOptions.TargetPlateSet().setRowId(context.targetPlateSetId))
            .setTargetPlateSource(new ReformatOptions.TargetPlateSource(PLATE_TYPE_12_WELLS))
            .setPreview(true);

        // Act (preview)
        PlateManager.ReformatResult result = PlateManager.get().reformat(container, user, options);

        // Assert
        assertNotNull(result.previewData());
        assertEquals("Expected array by column operation to generate 2 12-well plates.", 2, result.previewData().size());
        assertEquals("Expected 13 unique samples to be plated from the 2 source plates.", (Integer) 13, result.platedSampleCount());

        Set<Long> platedSamples = getSamples(result.previewData());
        Set<Long> controlSampleIntersection = new HashSet<>(platedSamples);
        controlSampleIntersection.retainAll(new HashSet<>(context.controlRowIds));

        assertTrue("Control samples should not be plated", controlSampleIntersection.isEmpty());

        // Act (saved)
        result = PlateManager.get().reformat(container, user, options.setPreview(false));

        // Assert
        assertNull(result.previewData());
        assertEquals("Expected target plate set to be used", context.targetPlateSetId, result.plateSetRowId());
        assertEquals(2, result.plateRowIds().size());

        Plate newPlate = PlateManager.get().getPlate(container, result.plateRowIds().getFirst());
        assertNotNull(newPlate);
        assertEquals(PLATE_TYPE_12_WELLS, newPlate.getPlateType());
        List<Long> sampleRowIds = context.sampleRowIds;

        try (var r = getPlateWellResults(newPlate.getRowId()))
        {
            while (r.next())
            {
                var sampleId = r.getInt(FieldKey.fromParts("sampleId"));
                var wellPosition = r.getString(FieldKey.fromParts("position"));

                switch (wellPosition)
                {
                    case "A1" -> assertEquals(sampleRowIds.get(0).intValue(), sampleId);
                    case "B1" -> assertEquals(sampleRowIds.get(1).intValue(), sampleId);
                    case "C1" -> assertEquals(sampleRowIds.get(2).intValue(), sampleId);
                    case "A2" -> assertEquals(sampleRowIds.get(3).intValue(), sampleId);
                    case "B2" -> assertEquals(sampleRowIds.get(4).intValue(), sampleId);
                    case "C2" -> assertEquals(sampleRowIds.get(5).intValue(), sampleId);
                    case "A3" -> assertEquals(sampleRowIds.get(6).intValue(), sampleId);
                    case "B3" -> assertEquals(sampleRowIds.get(7).intValue(), sampleId);
                    case "C3" -> assertEquals(sampleRowIds.get(8).intValue(), sampleId);
                    case "A4" -> assertEquals(sampleRowIds.get(9).intValue(), sampleId);
                    case "B4" -> assertEquals(sampleRowIds.get(10).intValue(), sampleId);
                    case "C4" -> assertEquals(sampleRowIds.get(11).intValue(), sampleId);
                }
            }
        }
    }

    @Test
    public void testReformatArrayByRow() throws Exception
    {
        // Arrange
        ReformatContext context = initializeReformatContext();

        ReformatOptions options = new ReformatOptions()
            .setOperation(ReformatOptions.ReformatOperation.arrayByRow)
            .setPlateRowIds(context.sourcePlates.stream().map(Plate::getRowId).toList())
            .setTargetPlateSet(new ReformatOptions.TargetPlateSet().setRowId(context.targetPlateSetId))
            .setTargetPlateSource(new ReformatOptions.TargetPlateSource(PLATE_TYPE_12_WELLS))
            .setPreview(true);

        // Act (preview)
        PlateManager.ReformatResult result = PlateManager.get().reformat(container, user, options);

        // Assert
        assertNotNull(result.previewData());
        assertEquals("Expected array by column operation to generate 2 12-well plates.", 2, result.previewData().size());
        assertEquals("Expected all unique samples to be plated from the 2 source plates.", (Integer) context.sampleRowIds.size(), result.platedSampleCount());

        Set<Long> platedSamples = getSamples(result.previewData());
        Set<Long> controlSampleIntersection = new HashSet<>(platedSamples);
        controlSampleIntersection.retainAll(new HashSet<>(context.controlRowIds));

        assertTrue("Control samples should not be plated", controlSampleIntersection.isEmpty());

        // Act (saved)
        result = PlateManager.get().reformat(container, user, options.setPreview(false));

        // Assert
        assertNull(result.previewData());
        assertEquals("Expected target plate set to be used", context.targetPlateSetId, result.plateSetRowId());
        assertEquals(2, result.plateRowIds().size());

        Plate newPlate = PlateManager.get().getPlate(container, result.plateRowIds().getFirst());
        assertNotNull(newPlate);
        assertEquals(PLATE_TYPE_12_WELLS, newPlate.getPlateType());
        List<Long> sampleRowIds = context.sampleRowIds;

        try (var r = getPlateWellResults(newPlate.getRowId()))
        {
            while (r.next())
            {
                var sampleId = r.getInt(FieldKey.fromParts("sampleId"));
                var wellPosition = r.getString(FieldKey.fromParts("position"));

                switch (wellPosition)
                {
                    case "A1" -> assertEquals(sampleRowIds.get(0).intValue(), sampleId);
                    case "A2" -> assertEquals(sampleRowIds.get(1).intValue(), sampleId);
                    case "A3" -> assertEquals(sampleRowIds.get(2).intValue(), sampleId);
                    case "A4" -> assertEquals(sampleRowIds.get(3).intValue(), sampleId);
                    case "B1" -> assertEquals(sampleRowIds.get(4).intValue(), sampleId);
                    case "B2" -> assertEquals(sampleRowIds.get(5).intValue(), sampleId);
                    case "B3" -> assertEquals(sampleRowIds.get(6).intValue(), sampleId);
                    case "B4" -> assertEquals(sampleRowIds.get(7).intValue(), sampleId);
                    case "C1" -> assertEquals(sampleRowIds.get(8).intValue(), sampleId);
                    case "C2" -> assertEquals(sampleRowIds.get(9).intValue(), sampleId);
                    case "C3" -> assertEquals(sampleRowIds.get(10).intValue(), sampleId);
                    case "C4" -> assertEquals(sampleRowIds.get(11).intValue(), sampleId);
                }
            }
        }
    }

    @Test
    public void testReformatArrayFromTemplate() throws Exception
    {
        // Arrange
        ReformatContext context = initializeReformatContext();

        // This template can support plating of 7 unique samples for the first plate and
        // 4 more unique samples on each subsequent plate.
        List<Map<String, Object>> templateData = List.of(
            CaseInsensitiveHashMap.of("wellLocation", "A1", "type", "SAMPLE", "wellGroup", "S1", PlateMetadataFields.barcode.name(), "BC-A1"),
            CaseInsensitiveHashMap.of("wellLocation", "A2", "type", "SAMPLE", PlateMetadataFields.barcode.name(), "BC-A2"),
            CaseInsensitiveHashMap.of("wellLocation", "A3", "type", "SAMPLE", PlateMetadataFields.barcode.name(), "BC-A3"),
            CaseInsensitiveHashMap.of("wellLocation", "A4", "type", "SAMPLE", PlateMetadataFields.barcode.name(), "BC-A4"),
            CaseInsensitiveHashMap.of("wellLocation", "B1", "type", "SAMPLE", "replicateGroup", "RBT1", PlateMetadataFields.barcode.name(), "BC-RB1"),
            CaseInsensitiveHashMap.of("wellLocation", "B2", "type", "SAMPLE", "replicateGroup", "RBT1", PlateMetadataFields.barcode.name(), "BC-RB1"),
            CaseInsensitiveHashMap.of("wellLocation", "B3", "type", "SAMPLE", "replicateGroup", "RBT2", PlateMetadataFields.barcode.name(), "BC-RB2"),
            CaseInsensitiveHashMap.of("wellLocation", "B4", "type", "SAMPLE", "replicateGroup", "RBT2", PlateMetadataFields.barcode.name(), "BC-RB2"),
            CaseInsensitiveHashMap.of("wellLocation", "C1", "type", "CONTROL", PlateMetadataFields.barcode.name(), "BC-C1"),
            CaseInsensitiveHashMap.of("wellLocation", "C2", "type", "SAMPLE", PlateMetadataFields.barcode.name(), "BC-C2"),
            CaseInsensitiveHashMap.of("wellLocation", "C3", "type", "CONTROL", PlateMetadataFields.barcode.name(), "BC-C3"),
            CaseInsensitiveHashMap.of("wellLocation", "C4", "type", "SAMPLE", "wellGroup", "S1", PlateMetadataFields.barcode.name(), "BC-C4")
        );

        Plate template = createPlateTemplate(PLATE_TYPE_12_WELLS, "Reformat array template", templateData);

        ReformatOptions options = new ReformatOptions()
            .setOperation(ReformatOptions.ReformatOperation.arrayFromTemplate)
            .setPlateRowIds(context.sourcePlates.stream().map(Plate::getRowId).toList())
            .setTargetPlateSet(new ReformatOptions.TargetPlateSet().setRowId(context.targetPlateSetId))
            .setTargetPlateSource(new ReformatOptions.TargetPlateSource(template))
            .setPreview(true);

        // Act (preview)
        PlateManager.ReformatResult result = PlateManager.get().reformat(container, user, options);

        // Assert
        assertNotNull(result.previewData());

        assertEquals("Expected array from template operation to generate 3 12-well plates.", 3, result.previewData().size());
        assertEquals("Expected all unique samples to be plated from the 2 source plates.", (Integer) context.sampleRowIds.size(), result.platedSampleCount());

        Set<Long> platedSamples = getSamples(result.previewData());
        Set<Long> controlSampleIntersection = new HashSet<>(platedSamples);
        controlSampleIntersection.retainAll(new HashSet<>(context.controlRowIds));

        assertTrue("Control samples should not be plated", controlSampleIntersection.isEmpty());

        // Act (saved)
        result = PlateManager.get().reformat(container, user, options.setPreview(false));

        // Assert
        assertNull(result.previewData());
        assertEquals("Expected target plate set to be used", context.targetPlateSetId, result.plateSetRowId());
        assertEquals(3, result.plateRowIds().size());

        Plate newPlate = PlateManager.get().getPlate(container, result.plateRowIds().getFirst());
        assertNotNull(newPlate);
        assertEquals(PLATE_TYPE_12_WELLS, newPlate.getPlateType());
        List<Long> sampleRowIds = context.sampleRowIds;

        // Verify the first plate
        try (var r = getPlateWellResults(newPlate.getRowId()))
        {
            int t = 0;
            while (r.next())
            {
                var sampleId = r.getInt(FieldKey.fromParts(WellTable.Column.SampleID.name()));
                var wellPosition = r.getString(FieldKey.fromParts("position"));

                switch (wellPosition)
                {
                    case "A1" -> assertEquals(sampleRowIds.get(0).intValue(), sampleId); // Group "S1"
                    case "A2" -> assertEquals(sampleRowIds.get(1).intValue(), sampleId);
                    case "A3" -> assertEquals(sampleRowIds.get(2).intValue(), sampleId);
                    case "A4" -> assertEquals(sampleRowIds.get(3).intValue(), sampleId);
                    case "B1" -> assertEquals(sampleRowIds.get(4).intValue(), sampleId); // Group "RBT1"
                    case "B2" -> assertEquals(sampleRowIds.get(4).intValue(), sampleId); // Group "RBT1"
                    case "B3" -> assertEquals(sampleRowIds.get(5).intValue(), sampleId); // Group "RBT2"
                    case "B4" -> assertEquals(sampleRowIds.get(5).intValue(), sampleId); // Group "RBT2"
                    case "C1" -> assertEquals(0, sampleId); // Control
                    case "C2" -> assertEquals(sampleRowIds.get(6).intValue(), sampleId);
                    case "C3" -> assertEquals(0, sampleId); // Control
                    case "C4" -> assertEquals(sampleRowIds.get(0).intValue(), sampleId); // Group "S1"
                }

                var barcode = r.getString(FieldKey.fromParts(PlateMetadataFields.barcode.name()));
                assertEquals(String.format("Expected barcode to match for position %s", wellPosition), templateData.get(t).get(PlateMetadataFields.barcode.name()), barcode);
                t++;
            }
        }

        newPlate = PlateManager.get().getPlate(container, result.plateRowIds().get(2));
        assertNotNull(newPlate);
        assertEquals(PLATE_TYPE_12_WELLS, newPlate.getPlateType());

        // Verify the third plate
        try (var r = getPlateWellResults(newPlate.getRowId()))
        {
            int t = 0;
            while (r.next())
            {
                var sampleId = r.getInt(FieldKey.fromParts(WellTable.Column.SampleID.name()));
                var wellPosition = r.getString(FieldKey.fromParts("position"));

                switch (wellPosition)
                {
                    case "A1" -> assertEquals(sampleRowIds.getFirst().intValue(), sampleId); // Group "S1"
                    case "A2" -> assertEquals(sampleRowIds.get(11).intValue(), sampleId);
                    case "A3" -> assertEquals(sampleRowIds.get(12).intValue(), sampleId);
                    case "A4" -> assertEquals(0, sampleId);
                    case "B1" -> assertEquals(sampleRowIds.get(4).intValue(), sampleId); // Group "RBT1"
                    case "B2" -> assertEquals(sampleRowIds.get(4).intValue(), sampleId); // Group "RBT1"
                    case "B3" -> assertEquals(sampleRowIds.get(5).intValue(), sampleId); // Group "RBT2"
                    case "B4" -> assertEquals(sampleRowIds.get(5).intValue(), sampleId); // Group "RBT2"
                    case "C1" -> assertEquals(0, sampleId); // Control
                    case "C2" -> assertEquals(0, sampleId);
                    case "C3" -> assertEquals(0, sampleId); // Control
                    case "C4" -> assertEquals(sampleRowIds.getFirst().intValue(), sampleId); // Group "S1"
                }

                var barcode = r.getString(FieldKey.fromParts(PlateMetadataFields.barcode.name()));
                assertEquals(String.format("Expected barcode to match for position %s", wellPosition), templateData.get(t).get(PlateMetadataFields.barcode.name()), barcode);
                t++;
            }
        }
    }
    
    private @NotNull Set<Long> getSamples(List<PlateManager.PreviewPlateData> plateData)
    {
        Set<Long> sampleIds = new HashSet<>();

        for (PlateManager.PreviewPlateData data : plateData)
        {
            for (Map<String, Object> well : data.data())
            {
                if (asLongElseNull(well.get(WellTable.Column.SampleID.name())) instanceof Long num)
                    sampleIds.add(num);
            }
        }

        return sampleIds;
    }

    @Test
    public void testReplicateZoneValidation() throws Exception
    {
        // Arrange
        String plateName = "testReplicateZoneValidation";
        String expectedErrorMessage = "Type \"Replicate\" is not supported for well %s. Specify a \"ReplicateGroup\" instead.";
        List<Map<String, Object>> sourcePlateData = new ArrayList<>();
        for (int i = 0; i < 5; i++)
        {
            var row = new CaseInsensitiveHashMap<>();
            row.put("wellLocation", "A" + (i + 1));
            row.put("type", "REPLICATE");
            row.put(PlateMetadataFields.barcode.name(), "BC-122");
            sourcePlateData.add(row);
        }

        // Act / Assert
        assertCreatePlateThrows(String.format(expectedErrorMessage, "A1"), PLATE_TYPE_96_WELLS, plateName, null, sourcePlateData);

        // Fixup rows by making all rows specify the same well group and resubmit
        sourcePlateData.forEach(row -> {
            row.put("type", "SAMPLE");
            row.put("replicateGroup", "Group A");
        });

        // Act (expect no errors)
        var newPlate = createPlate(PLATE_TYPE_96_WELLS, plateName, null, sourcePlateData);

        // Verify update validation
        var wellA3 = getWellRow(newPlate.getRowId(), "A3");
        wellA3.put("type", "REPLICATE");

        var errors = updateWells(List.of(wellA3), true);
        assertTrue(errors.hasErrors());
        assertEquals(String.format(expectedErrorMessage, "A3"), errors.getMessage());
    }

    @Test
    public void testReplicateWellValidation() throws Exception
    {
        // Arrange
        String plateName = "testReplicateWellValidation";
        List<Long> sampleRowIds = createSamples(5).stream().map(ExpObject::getRowId).sorted().toList();
        List<String> filledPositions = new ArrayList<>();

        Map<String, Object> commonWellValues = new CaseInsensitiveHashMap<>();
        commonWellValues.put("type", "SAMPLE");
        commonWellValues.put("replicateGroup", "R1");
        commonWellValues.put("concentration", 12.0);
        commonWellValues.put(PlateMetadataFields.barcode.name(), "BC-122");
        commonWellValues.put(PlateMetadataFields.opacity.name(), 3.14);
        commonWellValues.put(PlateMetadataFields.negativeControl.name(), 5.55);

        List<Map<String, Object>> sourcePlateData = new ArrayList<>();
        for (int i = 0; i < sampleRowIds.size(); i++)
        {
            String position = "A" + (i + 1);
            filledPositions.add(position);

            // All rows are the same except for wellLocation and sampleId
            var row = createWellRow(position, (String) commonWellValues.get("type"), sampleRowIds.get(i), null, (String) commonWellValues.get("replicateGroup"));
            row.putAll(commonWellValues);
            sourcePlateData.add(row);
        }

        // Act / Assert
        var expectedMessage = String.format("Replicate group \"%s\" contains mismatched well data. Ensure the same data is recorded for each well in this replicate group across all plates in the plate set.", commonWellValues.get("replicateGroup"));
        assertCreatePlateThrows(expectedMessage, PLATE_TYPE_96_WELLS, plateName, null, sourcePlateData);

        // Fixup rows by making all rows the same and resubmit
        sourcePlateData.forEach(row -> row.put("sampleId", sampleRowIds.getFirst()));

        // Act
        var newPlate = createPlate(PLATE_TYPE_96_WELLS, plateName, null, sourcePlateData);

        // Assert
        try (var r = getPlateWellResults(newPlate.getRowId()))
        {
            while (r.next())
            {
                var wellPosition = r.getString(FieldKey.fromParts("position"));

                if (filledPositions.contains(wellPosition))
                {
                    for (var entry : commonWellValues.entrySet())
                    {
                        String assertMessage = String.format("Unexpected value for \"%s\" in well position \"%s\".", entry.getKey(), wellPosition);
                        assertEquals(assertMessage, entry.getValue(), r.getObject(entry.getKey()));
                    }
                }
            }
        }

        var wellA3 = getWellRow(newPlate.getRowId(), "A3");

        // Verify update validation
        {
            wellA3.put(PlateMetadataFields.barcode.name(), null);

            var errors = updateWells(List.of(wellA3), true);
            assertTrue(errors.hasErrors());
            assertEquals(expectedMessage, errors.getMessage());
        }

        // Verify update only changing well metadata
        {
            var wellMetadataA3 = new CaseInsensitiveHashMap<>();
            wellMetadataA3.put("RowId", wellA3.get("RowId"));
            wellMetadataA3.put(PlateMetadataFields.barcode.name(), null);

            var errors = updateWells(List.of(wellMetadataA3), true);
            assertTrue(errors.hasErrors());
            assertEquals(expectedMessage, errors.getMessage());
        }
    }

    @Test
    public void testReplicateCrossPlateValidation() throws Exception
    {
        // Arrange
        PlateType plateType = PLATE_TYPE_96_WELLS;
        PlateSetImpl plateSetImpl = new PlateSetImpl();
        plateSetImpl.setName("testReplicateCrossPlateValidation");
        List<Long> sampleRowIds = createSamples(2).stream().map(ExpObject::getRowId).sorted().toList();

        List<Map<String, Object>> plate1Data = new ArrayList<>();
        plate1Data.add(createWellRow("A1", "SAMPLE", sampleRowIds.getFirst(), null, "R1"));
        plate1Data.add(createWellRow("A2", "SAMPLE", sampleRowIds.getFirst(), null, "R1"));
        plate1Data.add(createWellRow("A3", "SAMPLE", sampleRowIds.get(0), null, "R1"));

        List<Map<String, Object>> plate2Data = new ArrayList<>();
        plate2Data.add(createWellRow("B1", "SAMPLE", sampleRowIds.get(1), null, "R1"));
        plate2Data.add(createWellRow("B2", "SAMPLE", sampleRowIds.get(1), null, "R1"));
        plate2Data.add(createWellRow("B3", "SAMPLE", sampleRowIds.get(1), null, "R1"));

        List<Map<String, Object>> plate3Data = new ArrayList<>();
        plate2Data.add(createWellRow("C1", "SAMPLE", sampleRowIds.get(0), null, "R2"));
        plate2Data.add(createWellRow("C2", "SAMPLE", sampleRowIds.getFirst(), null, "R2"));
        plate2Data.add(createWellRow("C3", "SAMPLE", sampleRowIds.getFirst(), null, "R2"));

        var plateData = List.of(
            new PlateManager.PlateData(null, plateType.getRowId(), null, null, plate1Data),
            new PlateManager.PlateData(null, plateType.getRowId(), null, null, plate2Data),
            new PlateManager.PlateData(null, plateType.getRowId(), null, null, plate3Data)
        );

        // Act / Assert
        // Expect group "R1" to fail validation as it currently contains different samples across plates 1 and 2.
        var expectedMessage = "Replicate group \"R1\" contains mismatched well data. Ensure the same data is recorded for each well in this replicate group across all plates in the plate set.";
        assertCreatePlateSetThrows(expectedMessage, plateSetImpl, plateData, null);

        // Fixup rows by making all rows the same and resubmit
        plate2Data.forEach(row -> row.put("sampleId", sampleRowIds.getFirst()));

        // Assert (expect no errors)
        createPlateSet(plateSetImpl, plateData, null);
    }

    @Test
    public void testControlValidation() throws Exception
    {
        // Arrange
        List<ExpMaterial> samples = createSamples(4);
        List<Long> sampleRowIds = samples.stream().map(ExpObject::getRowId).sorted().toList();
        List<String> sampleNames = samples.stream().map(ExpObject::getName).sorted().toList();

        PlateSetImpl plateSetImpl = new PlateSetImpl();
        plateSetImpl.setType(PlateSetType.primary);
        PlateType plateType = PLATE_TYPE_12_WELLS;

        List<Map<String, Object>> PS1Data = List.of(
            createWellRow("A1", "SAMPLE", sampleRowIds.get(0)),
            createWellRow("A2", "SAMPLE", sampleRowIds.get(1)),
            createWellRow("A3", "SAMPLE", sampleRowIds.get(2))
        );

        var plateData1 = List.of(new PlateManager.PlateData("PS1", plateType.getRowId(), null, null, PS1Data));
        PlateSet plateSet1 = createPlateSet(plateSetImpl, plateData1, null);

        List<Map<String, Object>> dataPS2 = Arrays.asList(createWellRow("A1", "POSITIVE_CONTROL", sampleRowIds.getFirst()));
        var plateData2 = List.of(new PlateManager.PlateData("PS2", plateType.getRowId(), null, null, dataPS2));

        // Act / Assert
        // Since the sample of index 0 is on PS1's plate, it is not a valid control for PS2's plate
        String errorMsg = String.format("The sample \"%s\" is not a valid control.", sampleNames.getFirst());
        assertCreatePlateSetThrows(errorMsg, plateSetImpl, plateData2, plateSet1.getRowId());

        // Assert (expect no errors)
        List<Map<String, Object>> newDataPS2 = Arrays.asList(createWellRow("A1", "POSITIVE_CONTROL", sampleRowIds.get(3)));
        plateData2 = List.of(new PlateManager.PlateData("PS2", plateType.getRowId(), null, null, newDataPS2));
        createPlateSet(plateSetImpl, plateData2, plateSet1.getRowId());
    }

    @Test
    public void testBuiltInColumns() throws Exception
    {
        // Arrange
        PlateSetImpl PS1 = new PlateSetImpl();
        PS1.setType(PlateSetType.primary);
        var PPS = createPlateSet(PS1, null, null);
        var PPSPlate = createPlate(PLATE_TYPE_96_WELLS, "PPS_BuiltIn", PPS.getRowId(), null);

        PlateSetImpl PS2 = new PlateSetImpl();
        PS2.setType(PlateSetType.assay);
        var APS = createPlateSet(PS2, null, null);
        var APSPlate = createPlate(PLATE_TYPE_96_WELLS, "APS_BuiltIn", APS.getRowId(), null);

        Plate templatePS = createPlateTemplate(PLATE_TYPE_384_WELLS, "PT", null);

        // Act
        List<PlateCustomField> PPSPlateFields = PlateManager.get().getFields(container, PPSPlate.getRowId());
        List<PlateCustomField> APSPlateFields = PlateManager.get().getFields(container, APSPlate.getRowId());
        List<PlateCustomField> templatePlateFields = PlateManager.get().getFields(container, templatePS.getRowId());

        // Assert
        assertEquals(1, PPSPlateFields.size());
        assertEquals("SampleID", PPSPlateFields.getFirst().getName());

        assertEquals(4, APSPlateFields.size());
        assertEquals("Type", APSPlateFields.get(0).getName());
        assertEquals("WellGroup", APSPlateFields.get(1).getName());
        assertEquals("ReplicateGroup", APSPlateFields.get(2).getName());
        assertEquals("SampleID", APSPlateFields.get(3).getName());

        assertEquals(3, templatePlateFields.size());
        assertEquals("Type", APSPlateFields.get(0).getName());
        assertEquals("WellGroup", APSPlateFields.get(1).getName());
        assertEquals("ReplicateGroup", APSPlateFields.get(2).getName());
    }

    @Test
    public void testEnsureSampleWellTypeTriggerPopulates() throws Exception
    {
        // Arrange
        List<ExpMaterial> samples = createSamples(2);
        List<Long> sampleRowIds = samples.stream().map(ExpObject::getRowId).sorted().toList();

        List<Map<String, Object>> data = List.of(
            createWellRow("A1", "CONTROL", sampleRowIds.get(0)),
            createWellRow("A2", "", sampleRowIds.get(1))
        );

        // Act
        var plate = createPlate(PLATE_TYPE_12_WELLS, "TypeTriggerOne", null, data);

        // Assert
        List<String> types = List.of("CONTROL", "SAMPLE");
        try (var r = getPlateWellResults(plate.getRowId()))
        {
            for (int i = 0; i < 2; i++)
            {
                r.next();
                var type = r.getString(FieldKey.fromParts("type"));
                assertEquals(type, types.get(i));
            }
        }
    }

    @Test
    public void testEnsureSampleWellTypeTriggerRespectsType() throws Exception
    {
        // Arrange
        List<ExpMaterial> samples = createSamples(2);
        List<Long> sampleRowIds = samples.stream().map(ExpObject::getRowId).sorted().toList();

        List<Map<String, Object>> data = List.of(
            createWellRow("A1", "CONTROL", sampleRowIds.getFirst())
        );

        // Act
        var plate = createPlate(PLATE_TYPE_12_WELLS, "TypeTriggerTwo", null, data);
        var wellA1 = getWellRow(plate.getRowId(), "A1");
        wellA1.put("sampleId", sampleRowIds.get(1));
        updateWells(List.of(wellA1));

        // Assert
        try (var r = getPlateWellResults(plate.getRowId()))
        {
            r.next();
            var type = r.getString(FieldKey.fromParts("type"));
            assertEquals("CONTROL", type);
        }
    }

    @Test
    public void testCrossPlateSampleGroupValidation() throws Exception
    {
        // Arrange
        List<ExpMaterial> samples = createSamples(5);
        List<Long> sampleRowIds = samples.stream().map(ExpObject::getRowId).sorted().toList();

        List<Map<String, Object>> data = List.of(
            createWellRow("A1", "SAMPLE", sampleRowIds.get(0), "First", null),
            createWellRow("C1", "SAMPLE", sampleRowIds.get(0), "First", null),
            createWellRow("A2", "NEGATIVE_CONTROL", sampleRowIds.get(1), "First", null),
            createWellRow("C2", "NEGATIVE_CONTROL", sampleRowIds.get(1), "First", null),
            createWellRow("A3", "SAMPLE", sampleRowIds.get(2), "Second", null),
            createWellRow("C3", "SAMPLE", null, "Second", null)
        );

        var firstPlate = createPlate(PLATE_TYPE_12_WELLS, "FirstPlate", null, data);
        assertNotNull(firstPlate.getPlateSet());

        // Act
        // Attempt to put different samples in a sample group on a single plate
        {
            var wellC1 = getWellRow(firstPlate.getRowId(), "C1");
            wellC1.put("sampleId", sampleRowIds.get(1));
            var errors = updateWells(List.of(wellC1), true);

            // Assert
            assertEquals("Group \"First\" refers to multiple samples. Choose the same sample for all wells in this group.", errors.getMessage());
        }

        data = List.of(
            createWellRow("A1", "SAMPLE", sampleRowIds.get(0), "First", null),
            createWellRow("C1", "SAMPLE", sampleRowIds.get(0), "First", null),
            createWellRow("A2", "NEGATIVE_CONTROL", sampleRowIds.get(4), "Third", null),
            createWellRow("C2", "NEGATIVE_CONTROL", sampleRowIds.get(4), "Third", null),
            createWellRow("A3", "SAMPLE", null, "Second", null),
            createWellRow("C3", "SAMPLE", null, "Second", null),
            createWellRow("C4", "NEGATIVE_CONTROL", null, "First", null)
        );

        // Act
        // Successfully create a second plate which partially aligns on sample groups with the first plate
        var secondPlate = createPlate(PLATE_TYPE_12_WELLS, "SecondPlate", firstPlate.getPlateSet().getRowId(), data);

        // Act
        // Attempt to specify a mismatched sample on the second plate
        var wellC3 = getWellRow(secondPlate.getRowId(), "C3");
        wellC3.put("sampleId", sampleRowIds.get(1));
        var errors = updateWells(List.of(wellC3), true);

        // Assert
        assertEquals("Sample group \"Second\" contains mismatched samples across plates. Ensure the same sample is recorded for each well in this sample group across all plates in the plate set.", errors.getMessage());

        // Act
        // Attempt to specify a mismatched control on the second plate
//        var wellC4 = getWellRow(secondPlate.getRowId(), "C4");
//        wellC4.put("sampleId", sampleRowIds.get(4));
//        errors = updateWells(List.of(wellC4), true);
//
//        // Assert
//        assertEquals("Sample group \"First\" contains mismatched samples across plates. Ensure the same sample is recorded for each well in this sample group across all plates in the plate set.", errors.getMessage());

        // Act
        // Successfully align the samples across all groups
//        wellC3.put("sampleId", sampleRowIds.get(2));
//        wellC4.put("sampleId", sampleRowIds.get(1));
//        updateWells(List.of(wellC3, wellC4));
    }

    @Test // Issue 53578
    public void testDeleteSampleWellReferencesUponSampleDelete() throws Exception
    {
        // Arrange
        var props = List.of(new GWTPropertyDescriptor("name", "string"));
        var sampleTypeToBeDeleted = SampleTypeService.get().createSampleType(container, user, "SampleTypeDelete53578", null, props, emptyList(), -1, -1, -1, -1, "ST-${genId}", null);

        // Create samples in two different sample types
        var sampleRowIds = createSamples(3, sampleTypeToBeDeleted).stream().map(ExpObject::getRowId).sorted().toList();
        var defaultSampleRowIds = createSamples(2).stream().map(ExpObject::getRowId).sorted().toList();

        var pps = new PlateSetImpl();
        pps.setType(PlateSetType.primary);

        var wellData = List.of(
            createWellRow("A1", "SAMPLE", sampleRowIds.get(0)),
            createWellRow("A2", "SAMPLE", sampleRowIds.get(1)),
            createWellRow("A3", "SAMPLE", sampleRowIds.get(2)),
            createWellRow("B4", "SAMPLE", defaultSampleRowIds.get(0)),
            createWellRow("C1", "SAMPLE", defaultSampleRowIds.get(1))
        );

        var plateData = List.of(new PlateManager.PlateData(null, PLATE_TYPE_12_WELLS.getRowId(), null, null, wellData));
        var PPS = createPlateSet(pps, plateData, null);
        var ppsPlateRowId = PPS.getPlates().getFirst().getRowId();

        var aps = new PlateSetImpl();
        aps.setType(PlateSetType.assay);
        var APS = createPlateSet(aps, plateData, PPS.getRowId());
        var apsPlateRowId = APS.getPlates().getFirst().getRowId();

        // Act
        // Formerly, this would result in a foreign key violation on the assay.well table
        sampleTypeToBeDeleted.delete(user);

        // Assert
        // Verify sample references have been removed from every plate
        for (var plateRowId : List.of(ppsPlateRowId, apsPlateRowId))
        {
            try (var r = getPlateWellResults(plateRowId))
            {
                while (r.next())
                {
                    var sampleId = r.getInt(FieldKey.fromParts("sampleId"));
                    var wellPosition = r.getString(FieldKey.fromParts("position"));

                    switch (wellPosition)
                    {
                        // Expect only samples from the default sample type to remain in wells as
                        // the other sample type has been deleted.
                        case "B4" -> assertEquals(defaultSampleRowIds.get(0).intValue(), sampleId);
                        case "C1" -> assertEquals(defaultSampleRowIds.get(1).intValue(), sampleId);
                        default -> assertEquals(0, sampleId);
                    }
                }
            }
        }
    }

    private Plate createPlate(@NotNull PlateType plateType) throws Exception
    {
        return createPlate(plateType, null, null, null);
    }

    private Plate createPlate(
        @NotNull PlateType plateType,
        @Nullable String plateName,
        @Nullable Long plateSetId,
        @Nullable List<Map<String, Object>> plateData
    ) throws Exception
    {
        PlateImpl plate = new PlateImpl(container, plateName, null, plateType);
        return PlateManager.get().createAndSavePlate(container, user, plate, plateSetId, plateData);
    }

    private Plate createPlateTemplate(
        @NotNull PlateType plateType,
        @NotNull String templateName,
        @Nullable List<Map<String, Object>> templateData
    ) throws Exception
    {
        PlateImpl plate = new PlateImpl(container, templateName, null, plateType);
        plate.setTemplate(true);
        return PlateManager.get().createAndSavePlate(container, user, plate, null, templateData);
    }

    private void assertCreatePlateThrows(
        String expectedMessage,
        @NotNull PlateType plateType,
        @Nullable String plateName,
        @Nullable Long plateSetId,
        @Nullable List<Map<String, Object>> plateData
    )
    {
        try
        {
            createPlate(plateType, plateName, plateSetId, plateData);
        }
        catch (Throwable t)
        {
            assertEquals("Create plate did not throw the expected error.", expectedMessage, t.getMessage());
            return;
        }

        fail(String.format("Create plate failed to throw. Expected \"%s\".", expectedMessage));
    }

    private static PlateSetImpl createPlateSet(
        @NotNull PlateSetImpl plateSet,
        @Nullable List<PlateManager.PlateData> plates,
        @Nullable Long parentPlateSetId
    ) throws Exception
    {
        return PlateManager.get().createPlateSet(container, user, plateSet, plates, parentPlateSetId, null);
    }

    private void assertCreatePlateSetThrows(
        String expectedMessage,
        @NotNull PlateSetImpl plateSet,
        @Nullable List<PlateManager.PlateData> plates,
        @Nullable Long parentPlateSetId
    )
    {
        try
        {
            createPlateSet(plateSet, plates, parentPlateSetId);
        }
        catch (Throwable t)
        {
            assertEquals("Create plate set did not throw the expected error.", expectedMessage, t.getMessage());
            return;
        }

        fail(String.format("Create plate set failed to throw. Expected \"%s\".", expectedMessage));
    }

    private List<ExpMaterial> createSamples(int numSamples) throws Exception
    {
        return createSamples(numSamples, sampleType);
    }

    private List<ExpMaterial> createSamples(int numSamples, ExpSampleType sampleType) throws Exception
    {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = 0; i < numSamples; i++)
            rows.add(CaseInsensitiveHashMap.of());

        TableInfo table = QueryService.get().getUserSchema(user, container, SCHEMA_SAMPLES).getTable(sampleType.getName());

        var errors = new BatchValidationException();
        var insertedRows = table.getUpdateService().insertRows(user, container, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        List<Long> insertedRowIds = insertedRows.stream().map(row -> MapUtils.getLong(row,"RowId")).toList();
        return new ArrayList<>(ExperimentService.get().getExpMaterials(insertedRowIds));
    }

    private @NotNull TableInfo getWellTable()
    {
        var table = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME).getTable(WellTable.NAME);
        assertNotNull(table);
        return table;
    }

    private @NotNull Map<FieldKey, ColumnInfo> getWellTableColumns(TableInfo wellTable)
    {
        return QueryService.get().getColumns(wellTable, List.of(
            FieldKey.fromParts(PlateMetadataFields.barcode.name()),
            FieldKey.fromParts("concentration"),
            FieldKey.fromParts(PlateMetadataFields.negativeControl.name()),
            FieldKey.fromParts(PlateMetadataFields.opacity.name()),
            FieldKey.fromParts("position"),
            FieldKey.fromParts("rowId"),
            FieldKey.fromParts("sampleId"),
            FieldKey.fromParts("type"),
            FieldKey.fromParts("wellGroup"),
            FieldKey.fromParts("replicateGroup")
        ));
    }

    private Results getPlateWellResults(long plateRowId)
    {
        var filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("PlateId"), plateRowId);

        var wellTable = getWellTable();
        return QueryService.get().getSelectBuilder(wellTable).columns(getWellTableColumns(wellTable).values()).filter(filter).sort(new Sort("RowId")).select();
    }

    private Map<String, Object> getWellRow(long plateRowId, @NotNull String position)
    {
        var filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("PlateId"), plateRowId);
        filter.addCondition(FieldKey.fromParts("Position"), position);

        var wellTable = getWellTable();
        return QueryService.get().getSelectBuilder(wellTable)
                .columns(getWellTableColumns(wellTable).values())
                .filter(filter)
                .buildSqlSelector()
                .getMap();
    }

    private void updateWells(List<Map<String, Object>> rows) throws Exception
    {
        updateWells(rows, false);
    }

    private BatchValidationException updateWells(List<Map<String, Object>> rows, boolean expectErrors) throws Exception
    {
        TableInfo wellTable = getWellTable();
        QueryUpdateService qus = wellTable.getUpdateService();
        assertNotNull(qus);

        BatchValidationException errors = new BatchValidationException();
        try (DbScope.Transaction tx = PlateManager.get().ensureTransaction())
        {
            qus.updateRows(user, container, rows, null, errors, null, null);
            if (!expectErrors && errors.hasErrors())
                fail(errors.getMessage());

            tx.commit();
        }
        catch (BatchValidationException e)
        {
            if (!expectErrors && e.hasErrors())
                fail(e.getMessage());
        }

        if (expectErrors && !errors.hasErrors())
            fail("Expected an error when updating wells but an error did not occur.");

        return errors;
    }

    private Map<String, Object> createWellRow(String position, String type, Long sampleId)
    {
        return createWellRow(position, type, sampleId, null, null);
    }

    private Map<String, Object> createWellRow(
        String position,
        String type,
        Long sampleId,
        @Nullable String wellGroup,
        @Nullable String replicateGroup
    )
    {
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("wellLocation", position);
        row.put("type", type);
        if (wellGroup != null)
            row.put("wellGroup", wellGroup);
        if (replicateGroup != null)
            row.put("replicateGroup", replicateGroup);
        row.put("sampleId", sampleId);
        return row;
    }

    private Map<String, Object> wellWithMetdata(Map<String, Object> well, @Nullable Object concentration, @Nullable String barcode)
    {
        if (concentration != null)
            well.put("concentration", concentration);
        if (barcode != null)
            well.put(PlateMetadataFields.barcode.name(), barcode);
        return well;
    }
}
