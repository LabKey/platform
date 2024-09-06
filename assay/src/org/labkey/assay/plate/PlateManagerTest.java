package org.labkey.assay.plate;

import org.apache.logging.log4j.Logger;
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
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.labkey.api.exp.query.SamplesSchema.SCHEMA_SAMPLES;
import static org.labkey.api.util.JunitUtil.deleteTestContainer;

public final class PlateManagerTest
{
    private static final Logger LOG = LogHelper.getLogger(PlateManagerTest.class, "jUnit tests for Plates");

    private static Integer ARCHIVED_PLATE_SET_ID;
    private static Integer EMPTY_PLATE_SET_ID;
    private static Integer FULL_PLATE_SET_ID;

    private static PlateType PLATE_TYPE_12_WELLS;
    private static PlateType PLATE_TYPE_96_WELLS;
    private static PlateType PLATE_TYPE_384_WELLS;

    private static Container container;
    private static ExpSampleType sampleType;
    private static User user;

    private enum PlateMetadataFields
    {
        barcode,
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

        int plateId = PlateManager.get().save(container, user, template);

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

        List<WellGroup> wellGroups = savedTemplate.getWellGroups();
        assertEquals(3, wellGroups.size());

        // TsvPlateTypeHandler creates two CONTROL well groups "Positive" and "Negative"
        List<WellGroup> controlWellGroups = savedTemplate.getWellGroups(WellGroup.Type.CONTROL);
        assertEquals(2, controlWellGroups.size());

        List<WellGroup> sampleWellGroups = savedTemplate.getWellGroups(WellGroup.Type.SAMPLE);
        assertEquals(1, sampleWellGroups.size());
        WellGroup savedWg1 = sampleWellGroups.get(0);
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

        int newPlateId = PlateManager.get().save(container, user, savedTemplate);
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
        List<WellGroup> updatedControlWellGroups = updatedTemplate.getWellGroups(WellGroup.Type.CONTROL);
        assertEquals(1, updatedControlWellGroups.size());

        // verify added positions
        assertEquals(2, updatedControlWellGroups.get(0).getPositions().size());

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
        List<Plate> plates = plateSet.getPlates();
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
        Plate plate = PlateManager.get().createPlateTemplate(container, TsvPlateLayoutHandler.TYPE, PLATE_TYPE_384_WELLS);
        plate.setName("my plate template");
        int templateId = PlateManager.get().save(container, user, plate);
        Plate template = PlateManager.get().getPlate(container, templateId);

        // Assert
        assertNotNull("Expected plate template to be persisted", template);
        assertTrue("Expected saved plate to have the template field set to true", template.isTemplate());

        plate = PlateManager.get().createPlate(container, TsvPlateLayoutHandler.TYPE, PLATE_TYPE_96_WELLS);
        plate.setName("non plate template");
        PlateManager.get().save(container, user, plate);

        // Verify only plate templates are returned
        List<Plate> templates = PlateManager.get().getPlateTemplates(container);
        assertFalse("Expected there to be a plate template", templates.isEmpty());
        for (Plate t : templates)
            assertTrue("Expected saved plate to have the template field set to true", t.isTemplate());
    }

    @Test
    public void testCreatePlateMetadata() throws Exception
    {
        Plate plate = PlateManager.get().createPlateTemplate(container, TsvPlateLayoutHandler.TYPE, PLATE_TYPE_384_WELLS);
        plate.setName("new plate with metadata");
        int plateId = PlateManager.get().save(container, user, plate);

        // Assert
        assertTrue("Expected saved plateId to be returned", plateId != 0);

        List<PlateCustomField> fields = PlateManager.get().getPlateMetadataFields(container, user);

        // Verify returned sorted by name should include built in as well as custom created fields
        assertEquals("Expected plate custom fields", 7, fields.size());

        List<String> metadataFields = List.of("Amount", "AmountUnits", PlateMetadataFields.barcode.name(), "Concentration", "ConcentrationUnits", PlateMetadataFields.negativeControl.name(), PlateMetadataFields.opacity.name());
        for (int i=0; i < metadataFields.size(); i++)
        {
            String fieldName = metadataFields.get(i);
            assertEquals(String.format("Expected %s custom field", fieldName), fieldName, fields.get(i).getName());
        }

        // assign custom fields to the plate
        assertEquals("Expected custom fields to be added to the plate", 7, PlateManager.get().addFields(container, user, plateId, fields).size());

        // remove amount and amountUnits metadata fields
        fields = PlateManager.get().removeFields(container, user, plateId, List.of(fields.get(0), fields.get(1)));
        assertEquals("Expected 5 plate custom fields", 5, fields.size());
        assertEquals("Expected Concentration custom field", "Concentration", fields.get(0).getName());
        assertEquals("Expected ConcentrationUnits custom field", "ConcentrationUnits", fields.get(1).getName());

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
        try (Results r = QueryService.get().select(wellTable, columns.values(), filter, new Sort("Col")))
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
            CaseInsensitiveHashMap.of(
                "wellLocation", "A1",
                "concentration", 2.25,
                    PlateMetadataFields.barcode.name(), "B1234"
            ),
            CaseInsensitiveHashMap.of(
                "wellLocation", "A2",
                "concentration", 1.25,
                    PlateMetadataFields.barcode.name(), "B5678"
            )
        );

        Plate plate = createPlate(PLATE_TYPE_96_WELLS, "hit selection plate", null, rows);
        assertEquals("Expected 2 plate custom fields", 2, plate.getCustomFields().size());

        TableInfo wellTable = getWellTable();
        FieldKey fkConcentration = FieldKey.fromParts("concentration");
        FieldKey fkBarcode = FieldKey.fromParts(PlateMetadataFields.barcode.name());
        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(wellTable, List.of(fkConcentration, fkBarcode));

        // verify that well data was added
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());
        filter.addCondition(FieldKey.fromParts("Row"), 0);
        try (Results r = QueryService.get().select(wellTable, columns.values(), filter, new Sort("Col")))
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
    public void testGetWellSampleData()
    {
        // Act
        List<Integer> sampleIds = List.of(0, 3, 5, 8, 10, 11, 12, 13, 15, 17, 19);
        Pair<Integer, List<Map<String, Object>>> wellSampleDataFilledFull = PlateManager.get().getWellSampleData(container, sampleIds, 2, 3, 0);
        Pair<Integer, List<Map<String, Object>>> wellSampleDataFilledPartial = PlateManager.get().getWellSampleData(container, sampleIds, 2, 3, 6);

        // Assert
        assertEquals(wellSampleDataFilledFull.first, 6, 0);
        List<String> wellLocations = List.of("A1", "A2", "A3", "B1", "B2", "B3");
        for (int i = 0; i < wellSampleDataFilledFull.second.size(); i++)
        {
            Map<String, Object> well = wellSampleDataFilledFull.second.get(i);
            assertEquals(well.get("sampleId"), sampleIds.get(i));
            assertEquals(well.get("wellLocation"), wellLocations.get(i));
        }

        assertEquals(wellSampleDataFilledPartial.first, 11, 0);
        for (int i = 0; i < wellSampleDataFilledPartial.second.size(); i++)
        {
            Map<String, Object> well = wellSampleDataFilledPartial.second.get(i);
            assertEquals(well.get("sampleId"), sampleIds.get(i + 6));
            assertEquals(well.get("wellLocation"), wellLocations.get(i));
        }

        // Act
        try
        {
            PlateManager.get().getWellSampleData(container, Collections.emptyList(), 2, 3, 0);
        }
        // Assert
        catch (IllegalArgumentException e)
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
            CaseInsensitiveHashMap.of(
                "wellLocation", "A1",
                "sampleId", sample1.getRowId(),
                "type", "SAMPLE",
                "concentration", 2.25,
                    PlateMetadataFields.barcode.name(), "B1234"
            ),
            CaseInsensitiveHashMap.of(
                "wellLocation", "A2",
                "sampleId", sample2.getRowId(),
                "type", "SAMPLE",
                "concentration", 1.25,
                    PlateMetadataFields.barcode.name(), "B5678"
            )
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
        Object[] row1 = result.get(0);
        String[] valuesRow1 = new String[]{"myPlate", plate.getBarcode(), "A1", "96", sample1.getName(), "B1234", "2.25"};
        for (int i = 0; i < row1.length; i++)
            assertEquals(row1[i].toString(), valuesRow1[i]);

        Object[] row2 = result.get(1);
        String[] valuesRow2 = new String[]{"myPlate", plate.getBarcode(), "A2", "96", sample2.getName(), "B5678", "1.25"};
        for (int i = 0; i < row1.length; i++)
            assertEquals(row2[i].toString(), valuesRow2[i]);
    }

    private void assertWorklistThrows(String message, Integer sourceRowId, Integer destinationRowId, List<FieldKey> sourceIncludedMetadataCols, List<FieldKey> destinationIncludedMetadataCols) throws Exception
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

        List<Map<String, Object>> rows1 = List.of(
            CaseInsensitiveHashMap.of(
                "wellLocation", "A1",
                "sampleId", sample1.getRowId(),
                "type", "SAMPLE",
                "concentration", 2.25,
                    PlateMetadataFields.barcode.name(), "B1234"
            ),
            CaseInsensitiveHashMap.of(
                "wellLocation", "A2",
                "sampleId", sample2.getRowId(),
                "type", "SAMPLE",
                "concentration", 1.25,
                    PlateMetadataFields.barcode.name(), "B5678"
            )
        );
        Plate plateSource = createPlate(PLATE_TYPE_96_WELLS, "myPlate1", null, rows1);

        List<Map<String, Object>> rows2 = List.of(
            CaseInsensitiveHashMap.of(
                "wellLocation", "A1",
                "type", "SAMPLE",
                "sampleId", sample2.getRowId()
            ),
            CaseInsensitiveHashMap.of(
                "wellLocation", "A2",
                "type", "SAMPLE",
                "sampleId", sample1.getRowId()
            ),
            CaseInsensitiveHashMap.of(
                "wellLocation", "A3",
                "type", "SAMPLE",
                "sampleId", sample2.getRowId()
            )
        );
        Plate plateDestination = createPlate(PLATE_TYPE_96_WELLS, "myPlate2", null, rows2);

        // Act
        List<FieldKey> sourceIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateSource.getPlateSet(), container, user, cf);
        List<FieldKey> destinationIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateDestination.getPlateSet(), container, user, cf);
        List<Object[]> plateDataRows = PlateManager.get().getWorklist(plateSource.getPlateSet().getRowId(), plateDestination.getPlateSet().getRowId(), sourceIncludedMetadataCols, destinationIncludedMetadataCols, container, user);

        // Assert
        Object[] row1 = plateDataRows.get(0);
        String[] valuesRow1 = new String[]{"myPlate1", plateSource.getBarcode(), "A1", "96", sample1.getName(), "B1234", "2.25", "myPlate2", plateDestination.getBarcode(), "A2", "96"};
        for (int i = 0; i < row1.length; i++)
            assertEquals(row1[i].toString(), valuesRow1[i]);

        Object[] row2 = plateDataRows.get(1);
        String[] valuesRow2 = new String[]{"myPlate1", plateSource.getBarcode(),"A2", "96", sample2.getName(), "B5678", "1.25", "myPlate2", plateDestination.getBarcode(), "A1", "96"};
        for (int i = 0; i < row2.length; i++)
            assertEquals(row2[i].toString(), valuesRow2[i]);

        Object[] row3 = plateDataRows.get(2);
        String[] valuesRow3 = new String[]{"myPlate1", plateSource.getBarcode(),"A2", "96", sample2.getName(), "B5678", "1.25", "myPlate2", plateDestination.getBarcode(), "A3", "96"};
        for (int i = 0; i < row3.length; i++)
            assertEquals(row3[i].toString(), valuesRow3[i]);
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
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A1",
                        "sampleId", sample1.getRowId(),
                        "type", "SAMPLE",
                        "concentration", 2.25,
                        PlateMetadataFields.barcode.name(), "B1234"
                ),
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A2",
                        "sampleId", sample2.getRowId(),
                        "type", "SAMPLE",
                        "concentration", 1.25,
                        PlateMetadataFields.barcode.name(), "B5678"
                )
        );
        Plate plateSource = createPlate(PLATE_TYPE_96_WELLS, "myPlate1", null, rows1);

        List<Map<String, Object>> rows2 = List.of(
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A1",
                        "type", "SAMPLE",
                        "sampleId", sample2.getRowId()
                )
        );
        Plate plateDestination = createPlate(PLATE_TYPE_96_WELLS, "myPlate2", null, rows2);

        // Act
        List<FieldKey> sourceIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateSource.getPlateSet(), container, user, cf);
        List<FieldKey> destinationIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateDestination.getPlateSet(), container, user, cf);
        List<Object[]> plateDataRows = PlateManager.get().getWorklist(plateSource.getPlateSet().getRowId(), plateDestination.getPlateSet().getRowId(), sourceIncludedMetadataCols, destinationIncludedMetadataCols, container, user);

        // Assert
        Object[] row1 = plateDataRows.get(0);
        String[] valuesRow1 = new String[]{"myPlate1", plateSource.getBarcode(), "A1", "96", sample1.getName(), "B1234", "2.25", null, null, null, null};
        for (int i = 0; i < row1.length; i++)
            assertEquals(row1[i] == null ? null : row1[i].toString(), valuesRow1[i]);

        Object[] row2 = plateDataRows.get(1);
        String[] valuesRow2 = new String[]{"myPlate1", plateSource.getBarcode(),"A2", "96", sample2.getName(), "B5678", "1.25", "myPlate2", plateDestination.getBarcode(), "A1", "96"};
        for (int i = 0; i < row2.length; i++)
            assertEquals(row2[i].toString(), valuesRow2[i]);
    }

    @Test
    public void testGetWorklistSingleSampleManyToMany() throws Exception
    {
        // Arrange
        ContainerFilter cf = ContainerFilter.Type.CurrentAndSubfolders.create(container, user);
        ExpMaterial sample = createSamples(1).get(0);

        List<Map<String, Object>> rows1 = List.of(
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A1",
                        "sampleId", sample.getRowId(),
                        "type", "SAMPLE",
                        "concentration", 2.25,
                        PlateMetadataFields.barcode.name(), "B1234"
                ),
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A2",
                        "sampleId", sample.getRowId(),
                        "type", "SAMPLE",
                        "concentration", 1.25,
                        PlateMetadataFields.barcode.name(), "B5678"
                ),
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A3",
                        "sampleId", sample.getRowId(),
                        "type", "SAMPLE",
                        "concentration", 1.00,
                        PlateMetadataFields.barcode.name(), "B910"
                )
        );
        Plate plateSource = createPlate(PLATE_TYPE_96_WELLS, "myPlate1", null, rows1);

        List<Map<String, Object>> rows2 = List.of(
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A1",
                        "type", "SAMPLE",
                        "sampleId", sample.getRowId()
                ),
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A2",
                        "type", "SAMPLE",
                        "sampleId", sample.getRowId()
                )
        );
        Plate plateDestination = createPlate(PLATE_TYPE_96_WELLS, "myPlate2", null, rows2);

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
        ExpMaterial sample = createSamples(3).get(0);

        List<Map<String, Object>> rows1 = List.of(
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A1",
                        "sampleId", sample.getRowId(),
                        "type", "SAMPLE",
                        "concentration", 2.25,
                        PlateMetadataFields.barcode.name(), "B1234"
                ),
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A2",
                        "sampleId", sample.getRowId(),
                        "type", "SAMPLE",
                        "concentration", 1.25,
                        PlateMetadataFields.barcode.name(), "B5678"
                ),
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A3",
                        "sampleId", sample.getRowId(),
                        "type", "SAMPLE",
                        "concentration", 1.00,
                        PlateMetadataFields.barcode.name(), "B910"
                )
        );
        Plate plateSource = createPlate(PLATE_TYPE_96_WELLS, "myPlate1", null, rows1);

        List<Map<String, Object>> rows2 = List.of(
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A2",
                        "type", "SAMPLE",
                        "sampleId", sample.getRowId()
                ),
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A3",
                        "type", "SAMPLE",
                        "sampleId", sample.getRowId()
                ),
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A4",
                        "type", "SAMPLE",
                        "sampleId", sample.getRowId()
                )
        );
        Plate plateDestination = createPlate(PLATE_TYPE_96_WELLS, "myPlate2", null, rows2);

        // Act
        List<FieldKey> sourceIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateSource.getPlateSet(), container, user, cf);
        List<FieldKey> destinationIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateDestination.getPlateSet(), container, user, cf);
        List<Object[]> plateDataRows = PlateManager.get().getWorklist(plateSource.getPlateSet().getRowId(), plateDestination.getPlateSet().getRowId(), sourceIncludedMetadataCols, destinationIncludedMetadataCols, container, user);

        // Assert
        Object[] row1 = plateDataRows.get(0);
        String[] valuesRow1 = new String[]{"myPlate1", plateSource.getBarcode(), "A1", "96", sample.getName(), "B1234", "2.25", "myPlate2", plateDestination.getBarcode(), "A2", "96"};
        for (int i = 0; i < row1.length; i++)
            assertEquals(row1[i].toString(), valuesRow1[i]);

        Object[] row2 = plateDataRows.get(1);
        String[] valuesRow2 = new String[]{"myPlate1", plateSource.getBarcode(),"A2", "96", sample.getName(), "B5678", "1.25", "myPlate2", plateDestination.getBarcode(), "A3", "96"};
        for (int i = 0; i < row2.length; i++)
            assertEquals(row2[i].toString(), valuesRow2[i]);

        Object[] row3 = plateDataRows.get(2);
        String[] valuesRow3 = new String[]{"myPlate1", plateSource.getBarcode(),"A3", "96", sample.getName(), "B910", "1.0", "myPlate2", plateDestination.getBarcode(), "A4", "96"};
        for (int i = 0; i < row3.length; i++)
            assertEquals(row3[i].toString(), valuesRow3[i]);
    }

    @Test
    public void testGetWorklistSingleSampleOneToMany() throws Exception
    {
        // Arrange
        ContainerFilter cf = ContainerFilter.Type.CurrentAndSubfolders.create(container, user);
        ExpMaterial sample = createSamples(3).get(0);

        List<Map<String, Object>> rows1 = List.of(
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A1",
                        "sampleId", sample.getRowId(),
                        "type", "SAMPLE",
                        "concentration", 2.25,
                        PlateMetadataFields.barcode.name(), "B1234"
                )
        );
        Plate plateSource = createPlate(PLATE_TYPE_96_WELLS, "myPlate1", null, rows1);

        List<Map<String, Object>> rows2 = List.of(
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A2",
                        "type", "SAMPLE",
                        "sampleId", sample.getRowId()
                ),
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A3",
                        "type", "SAMPLE",
                        "sampleId", sample.getRowId()
                ),
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A4",
                        "type", "SAMPLE",
                        "sampleId", sample.getRowId()
                )
        );
        Plate plateDestination = createPlate(PLATE_TYPE_96_WELLS, "myPlate2", null, rows2);

        // Act
        List<FieldKey> sourceIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateSource.getPlateSet(), container, user, cf);
        List<FieldKey> destinationIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateDestination.getPlateSet(), container, user, cf);
        List<Object[]> plateDataRows = PlateManager.get().getWorklist(plateSource.getPlateSet().getRowId(), plateDestination.getPlateSet().getRowId(), sourceIncludedMetadataCols, destinationIncludedMetadataCols, container, user);

        // Assert
        Object[] row1 = plateDataRows.get(0);
        String[] valuesRow1 = new String[]{"myPlate1", plateSource.getBarcode(), "A1", "96", sample.getName(), "B1234", "2.25", "myPlate2", plateDestination.getBarcode(), "A2", "96"};
        for (int i = 0; i < row1.length; i++)
            assertEquals(row1[i].toString(), valuesRow1[i]);

        Object[] row2 = plateDataRows.get(1);
        String[] valuesRow2 = new String[]{"myPlate1", plateSource.getBarcode(),"A1", "96", sample.getName(), "B1234", "2.25", "myPlate2", plateDestination.getBarcode(), "A3", "96"};
        for (int i = 0; i < row2.length; i++)
            assertEquals(row2[i].toString(), valuesRow2[i]);

        Object[] row3 = plateDataRows.get(2);
        String[] valuesRow3 = new String[]{"myPlate1", plateSource.getBarcode(),"A1", "96", sample.getName(), "B1234", "2.25", "myPlate2", plateDestination.getBarcode(), "A4", "96"};
        for (int i = 0; i < row3.length; i++)
            assertEquals(row3[i].toString(), valuesRow3[i]);
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
            .setTargetPlateSet(new ReformatOptions.ReformatPlateSet().setRowId(EMPTY_PLATE_SET_ID));
    }

    @Test
    public void testReformatTargetPlateSet()
    {
        assertReformatThrows("Reformat options are required.", null);
        assertReformatThrows("An \"operation\" must be specified.", new ReformatOptions());

        assertReformatThrows("A \"targetPlateSet\" must be specified.", defaultOptions().setTargetPlateSet(null));
        assertReformatThrows(
            "Either a \"rowId\" or a \"type\" must be specified for \"targetPlateSet\".",
            defaultOptions().setTargetPlateSet(new ReformatOptions.ReformatPlateSet())
        );
        assertReformatThrows(
            "Either a \"rowId\" or a \"type\" must be specified for \"targetPlateSet\".",
            defaultOptions().setTargetPlateSet(new ReformatOptions.ReformatPlateSet().setRowId(null))
        );
        assertReformatThrows(
            "Either a \"rowId\" or a \"type\" must be specified for \"targetPlateSet\".",
            defaultOptions().setTargetPlateSet(new ReformatOptions.ReformatPlateSet().setRowId(0))
        );
        assertReformatThrows(
            "Either a \"rowId\" or a \"type\" can be specified for \"targetPlateSet\" but not both.",
            defaultOptions().setTargetPlateSet(new ReformatOptions.ReformatPlateSet().setRowId(1).setType(PlateSetType.assay))
        );

        PlateSet archivedPlateSet = PlateManager.get().getPlateSet(container, ARCHIVED_PLATE_SET_ID);
        assertNotNull(archivedPlateSet);

        assertReformatThrows(
            String.format("Plate Set \"%s\" is archived and cannot be modified.", archivedPlateSet.getName()),
            defaultOptions().setTargetPlateSet(new ReformatOptions.ReformatPlateSet().setRowId(ARCHIVED_PLATE_SET_ID))
        );

        PlateSet fullPlateSet = PlateManager.get().getPlateSet(container, FULL_PLATE_SET_ID);
        assertNotNull(fullPlateSet);

        assertReformatThrows(
            String.format("Plate Set \"%s\" is full and cannot include additional plates.", fullPlateSet.getName()),
            defaultOptions().setTargetPlateSet(new ReformatOptions.ReformatPlateSet().setRowId(FULL_PLATE_SET_ID))
        );
    }

    @Test
    public void testReformatSourcePlates() throws Exception
    {
        assertReformatThrows(
            "Either \"plateRowIds\" or \"plateSelectionKey\" can be specified but not both.",
            defaultOptions().setPlateRowIds(List.of(1234)).setPlateSelectionKey("1234")
        );
        assertReformatThrows(
            "Either \"plateRowIds\" or \"plateSelectionKey\" must be specified.",
            defaultOptions().setPlateRowIds(null).setPlateSelectionKey(" ")
        );
        assertReformatThrows("No source plates are specified.", defaultOptions().setPlateSelectionKey("1234"));

        List<Integer> withNulls = new ArrayList<>();
        withNulls.add(null);
        assertReformatThrows("An invalid null plate row id was specified.", defaultOptions().setPlateRowIds(withNulls));
        assertReformatThrows("An invalid plate row id (-10) was specified.", defaultOptions().setPlateRowIds(List.of(-10)));

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
                .setTargetPlateSet(new ReformatOptions.ReformatPlateSet().setType(PlateSetType.assay))
                .setTargetPlateTypeId(PLATE_TYPE_384_WELLS.getRowId())
                .setPreview(true);

        // Act (preview)
        var result = PlateManager.get().reformat(container, user, options);

        // Assert
        assertNotNull(result.previewData());
        assertEquals("Expected quadrant operation on 3 plates to generate 1 plate.", 1, result.previewData().size());

        var previewPlate = result.previewData().get(0);
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

        var newPlate = PlateManager.get().getPlate(container, result.plateRowIds().get(0));
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
        Integer targetPlateSetId = sourcePlate.getPlateSet().getRowId();

        ReformatOptions options = new ReformatOptions()
            .setOperation(ReformatOptions.ReformatOperation.reverseQuadrant)
            .setPlateRowIds(List.of(sourcePlate.getRowId()))
            .setTargetPlateSet(new ReformatOptions.ReformatPlateSet().setRowId(targetPlateSetId))
            .setTargetPlateTypeId(PLATE_TYPE_96_WELLS.getRowId())
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
        List<Integer> sampleRowIds = createSamples(6).stream().map(ExpObject::getRowId).sorted().toList();

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
        Integer targetPlateSetId = sourcePlate.getPlateSet().getRowId();

        ReformatOptions options = new ReformatOptions()
                .setOperation(ReformatOptions.ReformatOperation.columnCompression)
                .setPlateRowIds(List.of(sourcePlate.getRowId()))
                .setTargetPlateSet(new ReformatOptions.ReformatPlateSet().setRowId(targetPlateSetId))
                .setTargetPlateTypeId(PLATE_TYPE_12_WELLS.getRowId())
                .setPreview(true);

        // Act (preview)
        PlateManager.ReformatResult result = PlateManager.get().reformat(container, user, options);

        // Assert
        assertNotNull(result.previewData());
        assertEquals("Expected column compress operation on a 384-well plate to generate 1 12-well plates.", 1, result.previewData().size());

        List<Map<String, Object>> plateData = result.previewData().get(0).data();
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

        Plate newPlate = PlateManager.get().getPlate(container, result.plateRowIds().get(0));
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
        List<Integer> sampleRowIds = createSamples(6).stream().map(ExpObject::getRowId).sorted().toList();

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
        Integer targetPlateSetId = sourcePlate.getPlateSet().getRowId();

        ReformatOptions options = new ReformatOptions()
                .setOperation(ReformatOptions.ReformatOperation.rowCompression)
                .setPlateRowIds(List.of(sourcePlate.getRowId()))
                .setTargetPlateSet(new ReformatOptions.ReformatPlateSet().setRowId(targetPlateSetId))
                .setTargetPlateTypeId(PLATE_TYPE_12_WELLS.getRowId())
                .setPreview(true);

        // Act (preview)
        PlateManager.ReformatResult result = PlateManager.get().reformat(container, user, options);

        // Assert
        assertNotNull(result.previewData());
        assertEquals("Expected row compress operation on a 384-well plate to generate 1 12-well plates.", 1, result.previewData().size());

        List<Map<String, Object>> plateData = result.previewData().get(0).data();
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

        Plate newPlate = PlateManager.get().getPlate(container, result.plateRowIds().get(0));
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

    @Test
    public void testReplicateZoneValidation() throws Exception
    {
        // Arrange
        String plateName = "testReplicateZoneValidation";
        String expectedErrorMessage = "Replicates must specify a \"WellGroup\".";
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
        assertCreatePlateThrows(expectedErrorMessage, PLATE_TYPE_96_WELLS, plateName, null, sourcePlateData);

        // Fixup rows by making all rows specify the same well group and resubmit
        sourcePlateData.forEach(row -> row.put("wellGroup", "Group A"));

        // Act (expect no errors)
        var newPlate = createPlate(PLATE_TYPE_96_WELLS, plateName, null, sourcePlateData);

        // Verify update validation
        var wellA1 = getWellRow(newPlate.getRowId(), "A1");
        wellA1.put("wellGroup", null);

        var errors = updateWells(List.of(wellA1), true);
        assertTrue(errors.hasErrors());
        assertEquals(expectedErrorMessage, errors.getMessage());
    }

    @Test
    public void testReplicateWellValidation() throws Exception
    {
        // Arrange
        String plateName = "testReplicateWellValidation";
        List<Integer> sampleRowIds = createSamples(5).stream().map(ExpObject::getRowId).sorted().toList();
        List<String> filledPositions = new ArrayList<>();

        Map<String, Object> commonWellValues = new CaseInsensitiveHashMap<>();
        commonWellValues.put("type", "REPLICATE");
        commonWellValues.put("wellGroup", "R1");
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
            var row = createWellRow(position, (String) commonWellValues.get("type"), (String) commonWellValues.get("wellGroup"), sampleRowIds.get(i));
            row.putAll(commonWellValues);
            sourcePlateData.add(row);
        }

        // Act / Assert
        var expectedMessage = String.format("Replicate group \"%s\" contains mismatched well data. Ensure all data aligns for the replicates declared in these wells.", commonWellValues.get("wellGroup"));
        assertCreatePlateThrows(expectedMessage, PLATE_TYPE_96_WELLS, plateName, null, sourcePlateData);

        // Fixup rows by making all rows the same and resubmit
        sourcePlateData.forEach(row -> row.put("sampleId", sampleRowIds.get(0)));

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

        // Verify update validation
        var wellA3 = getWellRow(newPlate.getRowId(), "A3");
        wellA3.put(PlateMetadataFields.barcode.name(), null);

        var errors = updateWells(List.of(wellA3), true);
        assertTrue(errors.hasErrors());
        assertEquals(expectedMessage, errors.getMessage());
    }

    @Test
    public void testReplicateCrossPlateValidation() throws Exception
    {
        // Arrange
        PlateType plateType = PLATE_TYPE_96_WELLS;
        PlateSetImpl plateSetImpl = new PlateSetImpl();
        plateSetImpl.setName("testReplicateCrossPlateValidation");
        List<Integer> sampleRowIds = createSamples(2).stream().map(ExpObject::getRowId).sorted().toList();

        List<Map<String, Object>> plate1Data = new ArrayList<>();
        plate1Data.add(createWellRow("A1", "REPLICATE", "R1", sampleRowIds.get(0)));
        plate1Data.add(createWellRow("A2", "REPLICATE", "R1", sampleRowIds.get(0)));
        plate1Data.add(createWellRow("A3", "REPLICATE", "R1", sampleRowIds.get(0)));

        List<Map<String, Object>> plate2Data = new ArrayList<>();
        plate2Data.add(createWellRow("B1", "REPLICATE", "R1", sampleRowIds.get(1)));
        plate2Data.add(createWellRow("B2", "REPLICATE", "R1", sampleRowIds.get(1)));
        plate2Data.add(createWellRow("B3", "REPLICATE", "R1", sampleRowIds.get(1)));

        List<Map<String, Object>> plate3Data = new ArrayList<>();
        plate2Data.add(createWellRow("C1", "REPLICATE", "R2", sampleRowIds.get(0)));
        plate2Data.add(createWellRow("C2", "REPLICATE", "R2", sampleRowIds.get(0)));
        plate2Data.add(createWellRow("C3", "REPLICATE", "R2", sampleRowIds.get(0)));

        var plateData = List.of(
            new PlateManager.PlateData(null, plateType.getRowId(), null, null, plate1Data),
            new PlateManager.PlateData(null, plateType.getRowId(), null, null, plate2Data),
            new PlateManager.PlateData(null, plateType.getRowId(), null, null, plate3Data)
        );

        // Act / Assert
        // Expect group "R1" to fail validation as it currently contains different samples across plates 1 and 2.
        var expectedMessage = "Replicate group \"R1\" contains mismatched well data. Ensure all data aligns for the replicates declared in these wells.";
        assertCreatePlateSetThrows(expectedMessage, plateSetImpl, plateData, null);

        // Fixup rows by making all rows the same and resubmit
        plate2Data.forEach(row -> row.put("sampleId", sampleRowIds.get(0)));

        // Assert (expect no errors)
        createPlateSet(plateSetImpl, plateData, null);
    }

    @Test
    public void testControlValidation() throws Exception
    {
        // Arrange
        List<ExpMaterial> samples = createSamples(4);
        List<Integer> sampleRowIds = samples.stream().map(ExpObject::getRowId).sorted().toList();
        List<String> sampleNames = samples.stream().map(ExpObject::getName).sorted().toList();

        PlateSetImpl plateSetImpl = new PlateSetImpl();
        plateSetImpl.setType(PlateSetType.primary);
        PlateType plateType = PLATE_TYPE_12_WELLS;

        List<Map<String, Object>> PS1Data = new ArrayList<>();
        PS1Data.add(createWellRow("A1", "SAMPLE", null, sampleRowIds.get(0)));
        PS1Data.add(createWellRow("A2", "SAMPLE", null, sampleRowIds.get(1)));
        PS1Data.add(createWellRow("A3", "SAMPLE", null, sampleRowIds.get(2)));

        var plateData1 = List.of(new PlateManager.PlateData("PS1", plateType.getRowId(), null, null, PS1Data));
        PlateSet plateSet1 = createPlateSet(plateSetImpl, plateData1, null);

        List<Map<String, Object>> dataPS2 = Arrays.asList(createWellRow("A1", "POSITIVE_CONTROL", null, sampleRowIds.get(0)));
        var plateData2 = List.of(new PlateManager.PlateData("PS2", plateType.getRowId(), null, null, dataPS2));

        // Act / Assert
        // Since the sample of index 0 is on PS1's plate, it is not a valid control for PS2's plate
        String errorMsg = String.format("The sample \"%s\" is not a valid control.", sampleNames.get(0));
        assertCreatePlateSetThrows(errorMsg, plateSetImpl, plateData2, plateSet1.getRowId());

        // Assert (expect no errors)
        List<Map<String, Object>> newDataPS2 = Arrays.asList(createWellRow("A1", "POSITIVE_CONTROL", null, sampleRowIds.get(3)));
        plateData2 = List.of(new PlateManager.PlateData("PS2", plateType.getRowId(), null, null, newDataPS2));
        createPlateSet(plateSetImpl, plateData2, plateSet1.getRowId());
    }

    private Plate createPlate(@NotNull PlateType plateType) throws Exception
    {
        return createPlate(plateType, null, null, null);
    }

    private Plate createPlate(
        @NotNull PlateType plateType,
        @Nullable String plateName,
        @Nullable Integer plateSetId,
        @Nullable List<Map<String, Object>> plateData
    ) throws Exception
    {
        PlateImpl plate = new PlateImpl(container, plateName, null, plateType);
        return PlateManager.get().createAndSavePlate(container, user, plate, plateSetId, plateData);
    }

    private void assertCreatePlateThrows(
        String expectedMessage,
        @NotNull PlateType plateType,
        @Nullable String plateName,
        @Nullable Integer plateSetId,
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
        @Nullable Integer parentPlateSetId
    ) throws Exception
    {
        return PlateManager.get().createPlateSet(container, user, plateSet, plates, parentPlateSetId);
    }

    private void assertCreatePlateSetThrows(
        String expectedMessage,
        @NotNull PlateSetImpl plateSet,
        @Nullable List<PlateManager.PlateData> plates,
        @Nullable Integer parentPlateSetId
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
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = 0; i < numSamples; i++)
            rows.add(CaseInsensitiveHashMap.of());

        TableInfo table = QueryService.get().getUserSchema(user, container, SCHEMA_SAMPLES).getTable(sampleType.getName());

        var errors = new BatchValidationException();
        var insertedRows = table.getUpdateService().insertRows(user, container, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        List<Integer> insertedRowIds = insertedRows.stream().map(row -> (Integer) row.get("RowId")).toList();
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
            FieldKey.fromParts("wellGroup")
        ));
    }

    private Results getPlateWellResults(int plateRowId)
    {
        var filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("PlateId"), plateRowId);

        var wellTable = getWellTable();
        return QueryService.get().select(wellTable, getWellTableColumns(wellTable).values(), filter, new Sort("RowId"));
    }

    private Map<String, Object> getWellRow(int plateRowId, @NotNull String position)
    {
        var filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("PlateId"), plateRowId);
        filter.addCondition(FieldKey.fromParts("Position"), position);

        var wellTable = getWellTable();
        return QueryService.get().getSelectBuilder(wellTable)
                .columns(getWellTableColumns(wellTable).values())
                .filter(filter)
                .buildSqlSelector(null)
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

        return errors;
    }

    private Map<String, Object> createWellRow(String position, String type, String wellGroup, Integer sampleId)
    {
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("wellLocation", position);
        row.put("type", type);
        row.put("wellGroup", wellGroup);
        row.put("sampleId", sampleId);
        return row;
    }
}
