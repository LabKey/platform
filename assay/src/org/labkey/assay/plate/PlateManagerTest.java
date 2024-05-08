package org.labkey.assay.plate;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateCustomField;
import org.labkey.api.assay.plate.PlateLayoutHandler;
import org.labkey.api.assay.plate.PlateSet;
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
import org.labkey.assay.plate.model.WellBean;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.plate.query.WellTable;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.Arrays;
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
import static org.labkey.api.util.JunitUtil.deleteTestContainer;

public final class PlateManagerTest
{
    private static Container container;
    private static User user;
    private static ExpSampleType sampleType;

    @BeforeClass
    public static void setupTest() throws Exception
    {
        container = JunitUtil.getTestContainer();
        user = TestContext.get().getUser();

        PlateManager.get().deleteAllPlateData(container);
        Domain domain = PlateManager.get().getPlateMetadataDomain(container, user);
        if (domain != null)
            domain.delete(user);

        // create custom properties
        List<GWTPropertyDescriptor> customFields = List.of(
            new GWTPropertyDescriptor("barcode", "http://www.w3.org/2001/XMLSchema#string"),
            new GWTPropertyDescriptor("concentration", "http://www.w3.org/2001/XMLSchema#double"),
            new GWTPropertyDescriptor("negativeControl", "http://www.w3.org/2001/XMLSchema#double")
        );

        PlateManager.get().createPlateMetadataFields(container, user, customFields);

        // create sample type
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("col1", "string"));
        props.add(new GWTPropertyDescriptor("col2", "string"));
        sampleType = SampleTypeService.get().createSampleType(container, user, "SampleType1", null, props, emptyList(), 0, -1, -1, -1, null, null);
    }

    @After
    public void cleanupTest()
    {
        PlateManager.get().deleteAllPlateData(container);
    }

    @AfterClass
    public static void onComplete()
    {
        deleteTestContainer();
        container = null;
        user = null;
    }

    @Test
    public void createPlateTemplate() throws Exception
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
                new PlateManager.CreatePlateSetPlate("testAccessPlateByIdentifiersFirst", plateType.getRowId(), null),
                new PlateManager.CreatePlateSetPlate("testAccessPlateByIdentifiersSecond", plateType.getRowId(), null),
                new PlateManager.CreatePlateSetPlate("testAccessPlateByIdentifiersThird", plateType.getRowId(), null)
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
        PlateType plateType = PlateManager.get().getPlateType(16, 24);
        assertNotNull("384 well plate type was not found", plateType);
        Plate plate = PlateManager.get().createPlateTemplate(container, TsvPlateLayoutHandler.TYPE, plateType);
        plate.setName("my plate template");
        int templateId = PlateManager.get().save(container, user, plate);
        Plate template = PlateManager.get().getPlate(container, templateId);

        // Assert
        assertNotNull("Expected plate template to be persisted", template);
        assertTrue("Expected saved plate to have the template field set to true", template.isTemplate());

        plateType = PlateManager.get().getPlateType(8, 12);
        assertNotNull("96 well plate type was not found", plateType);

        plate = PlateManager.get().createPlate(container, TsvPlateLayoutHandler.TYPE, plateType);
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
        PlateType plateType = PlateManager.get().getPlateType(16, 24);
        assertNotNull("384 well plate type was not found", plateType);

        Plate plate = PlateManager.get().createPlateTemplate(container, TsvPlateLayoutHandler.TYPE, plateType);
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
                "properties/concentration", 1.25,
                "properties/negativeControl", 5.25
        ));

        qus.updateRows(user, container, rows, null, errors, null, null);
        if (errors.hasErrors())
            fail(errors.getMessage());

        well = wells.get(1);
        rows = List.of(CaseInsensitiveHashMap.of(
                "rowid", well.getRowId(),
                "properties/concentration", 2.25,
                "properties/negativeControl", 6.25
        ));

        qus.updateRows(user, container, rows, null, errors, null, null);
        if (errors.hasErrors())
            fail(errors.getMessage());

        FieldKey fkConcentration = FieldKey.fromParts("properties", "concentration");
        FieldKey fkNegativeControl = FieldKey.fromParts("properties", "negativeControl");
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
        // Arrange
        PlateType plateType = PlateManager.get().getPlateType(8, 12);
        assertNotNull("96 well plate type was not found", plateType);

        // Act
        List<Map<String, Object>> rows = List.of(
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A1",
                        "properties/concentration", 2.25,
                        "properties/barcode", "B1234")
                ,
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A2",
                        "properties/concentration", 1.25,
                        "properties/barcode", "B5678"
                )
        );

        PlateImpl plateImpl = new PlateImpl(container, "hit selection plate", plateType);
        Plate plate = PlateManager.get().createAndSavePlate(container, user, plateImpl, null, rows);
        assertEquals("Expected 2 plate custom fields", 2, plate.getCustomFields().size());

        TableInfo wellTable = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME).getTable(WellTable.NAME);
        FieldKey fkConcentration = FieldKey.fromParts("properties", "concentration");
        FieldKey fkBarcode = FieldKey.fromParts("properties", "barcode");
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
    public void getWellSampleData()
    {
        // Act
        int[] sampleIdsSorted = new int[]{0, 3, 5, 8, 10, 11, 12, 13, 15, 17, 19};
        Pair<Integer, List<Map<String, Object>>> wellSampleDataFilledFull = PlateManager.get().getWellSampleData(sampleIdsSorted, 2, 3, 0, container);
        Pair<Integer, List<Map<String, Object>>> wellSampleDataFilledPartial = PlateManager.get().getWellSampleData(sampleIdsSorted, 2, 3, 6, container);

        // Assert
        assertEquals(wellSampleDataFilledFull.first, 6, 0);
        ArrayList<String> wellLocations = new ArrayList<>(Arrays.asList("A1", "A2", "A3", "B1", "B2", "B3"));
        for (int i = 0; i < wellSampleDataFilledFull.second.size(); i++)
        {
            Map<String, Object> well = wellSampleDataFilledFull.second.get(i);
            assertEquals(well.get("sampleId"), sampleIdsSorted[i]);
            assertEquals(well.get("wellLocation"), wellLocations.get(i));
        }

        assertEquals(wellSampleDataFilledPartial.first, 11, 0);
        for (int i = 0; i < wellSampleDataFilledPartial.second.size(); i++)
        {
            Map<String, Object> well = wellSampleDataFilledPartial.second.get(i);
            assertEquals(well.get("sampleId"), sampleIdsSorted[i + 6]);
            assertEquals(well.get("wellLocation"), wellLocations.get(i));
        }

        // Act
        try
        {
            PlateManager.get().getWellSampleData(new int[]{}, 2, 3, 0, container);
        }
        // Assert
        catch (IllegalArgumentException e)
        {
            assertEquals("Expected validation exception", "No samples are in the current selection.", e.getMessage());
        }
    }

    @Test
    public void getInstrumentInstructions() throws Exception
    {
        // Arrange
        final ExpMaterial sample1 = ExperimentService.get().createExpMaterial(container, sampleType.generateSampleLSID().setObjectId("sampleOne").toString(), "sampleOne");
        sample1.setCpasType(sampleType.getLSID());
        sample1.save(user);
        final ExpMaterial sample2 = ExperimentService.get().createExpMaterial(container, sampleType.generateSampleLSID().setObjectId("sampleTwo").toString(), "sampleTwo");
        sample2.setCpasType(sampleType.getLSID());
        sample2.save(user);

        PlateType plateType = PlateManager.get().getPlateType(8, 12);
        assertNotNull("96 well plate type was not found", plateType);

        List<Map<String, Object>> rows = List.of(
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A1",
                        "sampleId", sample1.getRowId(),
                        "properties/concentration", 2.25,
                        "properties/barcode", "B1234")
                ,
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A2",
                        "sampleId", sample2.getRowId(),
                        "properties/concentration", 1.25,
                        "properties/barcode", "B5678"
                )
        );
        Plate plate = new PlateImpl(container, "myPlate", plateType);
        plate = PlateManager.get().createAndSavePlate(container, user, plate, null, rows);

        // Act
        List<FieldKey> includedMetadataCols = WellTable.getMetadataColumns(plate.getPlateSet(), user);
        List<Object[]> result = PlateManager.get().getInstrumentInstructions(plate.getPlateSet().getRowId(), includedMetadataCols, container, user);

        // Assert
        Object[] row1 = result.get(0);
        String[] valuesRow1 = new String[]{"myPlate", "A1", "96-well", "sampleOne", "B1234", "2.25"};
        for (int i = 0; i < row1.length; i++)
            assertEquals(row1[i].toString(), valuesRow1[i]);

        Object[] row2 = result.get(1);
        String[] valuesRow2 = new String[]{"myPlate", "A2", "96-well", "sampleTwo", "B5678", "1.25"};
        for (int i = 0; i < row1.length; i++)
            assertEquals(row2[i].toString(), valuesRow2[i]);
    }

    @Test
    public void getWorklist() throws Exception
    {
        // Arrange
        final ExpMaterial sample1 = ExperimentService.get().createExpMaterial(container, sampleType.generateSampleLSID().setObjectId("sampleA").toString(), "sampleA");
        sample1.setCpasType(sampleType.getLSID());
        sample1.save(user);
        final ExpMaterial sample2 = ExperimentService.get().createExpMaterial(container, sampleType.generateSampleLSID().setObjectId("sampleB").toString(), "sampleB");
        sample2.setCpasType(sampleType.getLSID());
        sample2.save(user);

        PlateType plateType = PlateManager.get().getPlateType(8, 12);
        assertNotNull("96 well plate type was not found", plateType);

        List<Map<String, Object>> rows1 = List.of(
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A1",
                        "sampleId", sample1.getRowId(),
                        "properties/concentration", 2.25,
                        "properties/barcode", "B1234")
                ,
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A2",
                        "sampleId", sample2.getRowId(),
                        "properties/concentration", 1.25,
                        "properties/barcode", "B5678"
                )
        );
        Plate plateSource = PlateManager.get().createAndSavePlate(container, user, new PlateImpl(container, "myPlate1", plateType), null, rows1);

        List<Map<String, Object>> rows2 = List.of(
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A1",
                        "sampleId", sample2.getRowId())
                ,
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A2",
                        "sampleId", sample1.getRowId())
                ,
                CaseInsensitiveHashMap.of(
                        "wellLocation", "A3",
                        "sampleId", sample2.getRowId())
        );
        Plate plateDestination = PlateManager.get().createAndSavePlate(container, user, new PlateImpl(container, "myPlate2", plateType), null, rows2);

        // Act
        List<FieldKey> sourceIncludedMetadataCols = WellTable.getMetadataColumns(plateSource.getPlateSet(), user);
        List<FieldKey> destinationIncludedMetadataCols = WellTable.getMetadataColumns(plateDestination.getPlateSet(), user);
        List<Object[]> plateDataRows = PlateManager.get().getWorklist(plateSource.getPlateSet().getRowId(), plateDestination.getPlateSet().getRowId(), sourceIncludedMetadataCols, destinationIncludedMetadataCols, container, user);

        // Assert
        Object[] row1 = plateDataRows.get(0);
        String[] valuesRow1 = new String[]{"myPlate1", "A1", "96-well", "sampleA", "B1234", "2.25", "myPlate2", "A2", "96-well"};
        for (int i = 0; i < row1.length; i++)
            assertEquals(row1[i].toString(), valuesRow1[i]);

        Object[] row2 = plateDataRows.get(1);
        String[] valuesRow2 = new String[]{"myPlate1", "A2", "96-well", "sampleB", "B5678", "1.25", "myPlate2", "A1", "96-well"};
        for (int i = 0; i < row2.length; i++)
            assertEquals(row2[i].toString(), valuesRow2[i]);

        Object[] row3 = plateDataRows.get(2);
        String[] valuesRow3 = new String[]{"myPlate1", "A2", "96-well", "sampleB", "B5678", "1.25", "myPlate2", "A3", "96-well"};
        for (int i = 0; i < row3.length; i++)
            assertEquals(row3[i].toString(), valuesRow3[i]);
    }
}
