package org.labkey.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
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
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.assay.plate.model.ReformatOptions;
import org.labkey.assay.plate.model.WellBean;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.plate.query.WellTable;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.labkey.api.util.JunitUtil.deleteTestContainer;

public final class PlateManagerTest
{
    private static Integer ARCHIVED_PLATE_SET_ID;
    private static Integer EMPTY_PLATE_SET_ID;
    private static Integer FULL_PLATE_SET_ID;

    private static PlateType PLATE_TYPE_12_WELLS;
    private static PlateType PLATE_TYPE_96_WELLS;
    private static PlateType PLATE_TYPE_384_WELLS;

    private static Container container;
    private static ExpSampleType sampleType;
    private static User user;

    @BeforeClass
    public static void setupTest() throws Exception
    {
        deleteTestContainer();

        container = JunitUtil.getTestContainer();
        user = TestContext.get().getUser();

        Domain domain = PlateManager.get().getPlateMetadataDomain(container, user);
        if (domain != null)
            domain.delete(user);

        // create custom properties
        {
            List<GWTPropertyDescriptor> customFields = List.of(
                new GWTPropertyDescriptor("barcode", "http://www.w3.org/2001/XMLSchema#string"),
                new GWTPropertyDescriptor("concentration", "http://www.w3.org/2001/XMLSchema#double"),
                new GWTPropertyDescriptor("negativeControl", "http://www.w3.org/2001/XMLSchema#double")
            );
            PlateManager.get().createPlateMetadataFields(container, user, customFields);
        }

        // create sample type
        {
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("col1", "string"));
            props.add(new GWTPropertyDescriptor("col2", "string"));
            sampleType = SampleTypeService.get().createSampleType(container, user, "SampleType1", null, props, emptyList(), 0, -1, -1, -1, null, null);
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

            archivedPlateSet = PlateManager.get().createPlateSet(container, user, archivedPlateSet, null, null);
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

            emptyPlateSet = PlateManager.get().createPlateSet(container, user, emptyPlateSet, null, null);
            assertEquals(Integer.valueOf(0), emptyPlateSet.getPlateCount());
            EMPTY_PLATE_SET_ID = emptyPlateSet.getRowId();
        }

        // create full plate set
        {
            PlateSetImpl fullPlateSet = new PlateSetImpl();
            fullPlateSet.setDescription("PlateManagerTest Full Plate Set");

            List<PlateManager.PlateData> fullPlates = new ArrayList<>();
            for (int i = 0; i < PlateSet.MAX_PLATES; i++)
                fullPlates.add(new PlateManager.PlateData(null, PLATE_TYPE_12_WELLS.getRowId(), null, null));

            fullPlateSet = PlateManager.get().createPlateSet(container, user, fullPlateSet, fullPlates, null);
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
        PlateType plateType = PlateManager.get().getPlateType(8, 12);
        assertNotNull("96 well plate type was not found", plateType);

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
        // Arrange
        PlateType plateType = PlateManager.get().getPlateType(8, 12);
        assertNotNull("96 well plate type was not found", plateType);

        // Act
        PlateImpl plateImpl = new PlateImpl(container, "testCreateAndSavePlate plate", plateType);
        Plate plate = PlateManager.get().createAndSavePlate(container, user, plateImpl, null, null);

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
        PlateType plateType = PlateManager.get().getPlateType(8, 12);
        assertNotNull("96 well plate type was not found", plateType);
        PlateSetImpl plateSetImpl = new PlateSetImpl();
        plateSetImpl.setName("testAccessPlateByIdentifiersPlateSet");
        ContainerFilter cf = ContainerFilter.Type.CurrentAndSubfolders.create(ContainerManager.getSharedContainer(), user);

        // Act
        PlateSet plateSet = PlateManager.get().createPlateSet(container, user, plateSetImpl, List.of(
                new PlateManager.PlateData("testAccessPlateByIdentifiersFirst", plateType.getRowId(), null, null),
                new PlateManager.PlateData("testAccessPlateByIdentifiersSecond", plateType.getRowId(), null, null),
                new PlateManager.PlateData("testAccessPlateByIdentifiersThird", plateType.getRowId(), null, null)
        ), null);

        // Assert
        assertTrue("Expected plateSet to have been persisted and provided with a rowId", plateSet.getRowId() > 0);
        List<Plate> plates = plateSet.getPlates(user);
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

        // Verify returned sorted by name
        assertEquals("Expected plate custom fields", 3, fields.size());
        assertEquals("Expected barcode custom field", "barcode", fields.get(0).getName());
        assertEquals("Expected concentration custom field", "concentration", fields.get(1).getName());
        assertEquals("Expected negativeControl custom field", "negativeControl", fields.get(2).getName());

        // assign custom fields to the plate
        assertEquals("Expected custom fields to be added to the plate", 3, PlateManager.get().addFields(container, user, plateId, fields).size());

        // verification when adding custom fields to the plate
        try
        {
            PlateManager.get().addFields(container, user, plateId, fields);
            fail("Expected a validation error when adding existing fields");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("Expected validation exception", "Failed to add plate custom fields. Custom field \"barcode\" already is associated with this plate.", e.getMessage());
        }

        // remove a plate custom field
        fields = PlateManager.get().removeFields(container, user, plateId, List.of(fields.get(0)));
        assertEquals("Expected 2 plate custom fields", 2, fields.size());
        assertEquals("Expected concentration custom field", "concentration", fields.get(0).getName());
        assertEquals("Expected negativeControl custom field", "negativeControl", fields.get(1).getName());

        // select wells
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("PlateId"), plateId);
        filter.addCondition(FieldKey.fromParts("Row"), 0);
        List<WellBean> wells = new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), filter, new Sort("Col")).getArrayList(WellBean.class);

        assertEquals("Expected 24 wells to be returned", 24, wells.size());

        // update
        TableInfo wellTable = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME).getTable(WellTable.NAME);
        QueryUpdateService qus = wellTable.getUpdateService();
        assertNotNull(qus);
        BatchValidationException errors = new BatchValidationException();

        // add metadata to 2 rows
        WellBean well = wells.get(0);
        List<Map<String, Object>> rows = List.of(CaseInsensitiveHashMap.of(
                "rowid", well.getRowId(),
                "concentration", 1.25,
                "negativeControl", 5.25
        ));

        qus.updateRows(user, container, rows, null, errors, null, null);
        if (errors.hasErrors())
            fail(errors.getMessage());

        well = wells.get(1);
        rows = List.of(CaseInsensitiveHashMap.of(
                "rowid", well.getRowId(),
                "concentration", 2.25,
                "negativeControl", 6.25
        ));

        qus.updateRows(user, container, rows, null, errors, null, null);
        if (errors.hasErrors())
            fail(errors.getMessage());

        FieldKey fkConcentration = FieldKey.fromParts("concentration");
        FieldKey fkNegativeControl = FieldKey.fromParts("negativeControl");
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
                "barcode", "B1234"
            ),
            CaseInsensitiveHashMap.of(
                "wellLocation", "A2",
                "concentration", 1.25,
                "barcode", "B5678"
            )
        );

        Plate plate = createPlate(PLATE_TYPE_96_WELLS, "hit selection plate", null, rows);
        assertEquals("Expected 2 plate custom fields", 2, plate.getCustomFields().size());

        TableInfo wellTable = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME).getTable(WellTable.NAME);
        FieldKey fkConcentration = FieldKey.fromParts("concentration");
        FieldKey fkBarcode = FieldKey.fromParts("barcode");
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
        ContainerFilter cf = ContainerFilter.Type.CurrentAndSubfolders.create(ContainerManager.getSharedContainer(), user);

        final ExpMaterial sample1 = ExperimentService.get().createExpMaterial(container, sampleType.generateSampleLSID().setObjectId("sampleOne").toString(), "sampleOne");
        sample1.setCpasType(sampleType.getLSID());
        sample1.save(user);
        final ExpMaterial sample2 = ExperimentService.get().createExpMaterial(container, sampleType.generateSampleLSID().setObjectId("sampleTwo").toString(), "sampleTwo");
        sample2.setCpasType(sampleType.getLSID());
        sample2.save(user);

        List<Map<String, Object>> rows = List.of(
            CaseInsensitiveHashMap.of(
                "wellLocation", "A1",
                "sampleId", sample1.getRowId(),
                "type", "SAMPLE",
                "concentration", 2.25,
                "barcode", "B1234")
            ,
            CaseInsensitiveHashMap.of(
                "wellLocation", "A2",
                "sampleId", sample2.getRowId(),
                "type", "SAMPLE",
                "concentration", 1.25,
                "barcode", "B5678"
            )
        );
        Plate plate = createPlate(PLATE_TYPE_96_WELLS, "myPlate", null, rows);

        // Act
        List<FieldKey> includedMetadataCols = PlateManager.get().getMetadataColumns(plate.getPlateSet(), container, user, cf);
        List<Object[]> result = PlateManager.get().getInstrumentInstructions(plate.getPlateSet().getRowId(), includedMetadataCols, container, user);

        // Assert
        Object[] row1 = result.get(0);
        String[] valuesRow1 = new String[]{"myPlate", "A1", "96", "sampleOne", "B1234", "2.25"};
        for (int i = 0; i < row1.length; i++)
            assertEquals(row1[i].toString(), valuesRow1[i]);

        Object[] row2 = result.get(1);
        String[] valuesRow2 = new String[]{"myPlate", "A2", "96", "sampleTwo", "B5678", "1.25"};
        for (int i = 0; i < row1.length; i++)
            assertEquals(row2[i].toString(), valuesRow2[i]);
    }

    @Test
    public void testGetWorklist() throws Exception
    {
        // Arrange
        ContainerFilter cf = ContainerFilter.Type.CurrentAndSubfolders.create(ContainerManager.getSharedContainer(), user);

        final ExpMaterial sample1 = ExperimentService.get().createExpMaterial(container, sampleType.generateSampleLSID().setObjectId("sampleA").toString(), "sampleA");
        sample1.setCpasType(sampleType.getLSID());
        sample1.save(user);
        final ExpMaterial sample2 = ExperimentService.get().createExpMaterial(container, sampleType.generateSampleLSID().setObjectId("sampleB").toString(), "sampleB");
        sample2.setCpasType(sampleType.getLSID());
        sample2.save(user);

        List<Map<String, Object>> rows1 = List.of(
            CaseInsensitiveHashMap.of(
                "wellLocation", "A1",
                "sampleId", sample1.getRowId(),
                "type", "SAMPLE",
                "concentration", 2.25,
                "barcode", "B1234")
            ,
            CaseInsensitiveHashMap.of(
                "wellLocation", "A2",
                "sampleId", sample2.getRowId(),
                "type", "SAMPLE",
                "concentration", 1.25,
                "barcode", "B5678"
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
        String[] valuesRow1 = new String[]{"myPlate1", "A1", "96", "sampleA", "B1234", "2.25", "myPlate2", "A2", "96"};
        for (int i = 0; i < row1.length; i++)
            assertEquals(row1[i].toString(), valuesRow1[i]);

        Object[] row2 = plateDataRows.get(1);
        String[] valuesRow2 = new String[]{"myPlate1", "A2", "96", "sampleB", "B5678", "1.25", "myPlate2", "A1", "96"};
        for (int i = 0; i < row2.length; i++)
            assertEquals(row2[i].toString(), valuesRow2[i]);

        Object[] row3 = plateDataRows.get(2);
        String[] valuesRow3 = new String[]{"myPlate1", "A2", "96", "sampleB", "B5678", "1.25", "myPlate2", "A3", "96"};
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
    public void testReformatExpand() throws Exception
    {
        // Arrange
        List<Map<String, Object>> sourcePlateData = List.of(
            CaseInsensitiveHashMap.of("wellLocation", "A1", "properties/barcode", "BC-A1"),
            CaseInsensitiveHashMap.of("wellLocation", "H12", "properties/barcode", "BC-H12"),
            CaseInsensitiveHashMap.of("wellLocation", "I13", "properties/barcode", "BC-I13"),
            CaseInsensitiveHashMap.of("wellLocation", "P24", "properties/barcode", "BC-P24")
        );
        Plate sourcePlate = createPlate(PLATE_TYPE_384_WELLS, "384-well source plate", null, sourcePlateData);
        Integer targetPlateSetId = sourcePlate.getPlateSet().getRowId();

        ReformatOptions options = new ReformatOptions()
            .setOperation(ReformatOptions.ReformatOperation.expand)
            .setTargetPlateSet(new ReformatOptions.ReformatPlateSet().setRowId(targetPlateSetId))
            .setOperationOptions(
                new ReformatOptions.OperationOptions()
                        .setFillStrategy(ReformatOptions.FillStrategy.reverseQuadrant)
                        .setTargetPlateTypeId(PLATE_TYPE_96_WELLS.getRowId())
            )
            .setPlateRowIds(List.of(sourcePlate.getRowId()))
            .setPreview(true);

        // Act (preview)
        PlateManager.ReformatResult result = PlateManager.get().reformat(container, user, options);

        // Assert
        assertNotNull(result.previewData());
        assertEquals("Expected reverse quadrant expansion of a 384-well plate to generate 4 96-well plates.", 4, result.previewData().size());

        for (int i = 0; i < result.previewData().size(); i++)
            assertEquals("Expected all generated plates to have 96-wells", 96, result.previewData().get(i).data().size());

        assertEquals("BC-A1", result.previewData().get(0).data().get(0).get("properties/barcode"));
        assertEquals("BC-H12", result.previewData().get(0).data().get(95).get("properties/barcode"));
        assertEquals("BC-I13", result.previewData().get(3).data().get(0).get("properties/barcode"));
        assertEquals("BC-P24", result.previewData().get(3).data().get(95).get("properties/barcode"));

        // Act (saved)
        result = PlateManager.get().reformat(container, user, options.setPreview(false));

        assertNull(result.previewData());
        assertEquals(targetPlateSetId, result.plateSetRowId());
        assertEquals(4, result.plateRowIds().size());
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
        PlateImpl plate = new PlateImpl(container, plateName, plateType);
        return PlateManager.get().createAndSavePlate(container, user, plate, plateSetId, plateData);
    }
}
