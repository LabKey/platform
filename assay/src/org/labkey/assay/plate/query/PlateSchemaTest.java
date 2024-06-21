package org.labkey.assay.plate.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.assay.plate.PlateImpl;
import org.labkey.assay.plate.PlateManager;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public final class PlateSchemaTest
{
    private static Container container;
    private static User user;
    private static PlateType PLATE_TYPE_12_WELL;

    @BeforeClass
    public static void setupTest()
    {
        deleteTestContainer();

        container = JunitUtil.getTestContainer();
        user = TestContext.get().getUser();

        PLATE_TYPE_12_WELL = PlateManager.get().getPlateType(3, 4);
        assertNotNull("12-well plate type was not found", PLATE_TYPE_12_WELL);
    }

    @AfterClass
    public static void cleanup()
    {
        deleteTestContainer();
        container = null;
        user = null;
    }

    private static void deleteTestContainer()
    {
        JunitUtil.deleteTestContainer();
    }

    @Test
    public void testPlateRenaming() throws Exception
    {
        Plate plate = PlateManager.get().createAndSavePlate(container, user, new PlateImpl(container, null, PLATE_TYPE_12_WELL), null, null);
        assertEquals("A null plate \"Name\" is expected to result in a plate with the \"Name\" being set to the \"PlateId\".", plate.getPlateId(), plate.getName());

        // Issue 50658: Clearing plate name should reset the plate name to equal the PlateId
        String nonEmptyName = "Non-Empty Plate Name";
        plate = updatePlate(plateRow(plate, PlateTable.Column.Name, nonEmptyName));
        assertEquals("Name not set as expected.", nonEmptyName, plate.getName());

        plate = updatePlate(plateRow(plate, PlateTable.Column.Name, ""));
        assertEquals("Setting \"Name\" to an empty value is expected to reset name to the value of \"PlateId\".", plate.getPlateId(), plate.getName());
    }

    private CaseInsensitiveHashMap<Object> plateRow(Plate plate, @Nullable PlateTable.Column column, @Nullable Object value)
    {
        var row = new CaseInsensitiveHashMap<>();
        row.put(PlateTable.Column.RowId.name(), plate.getRowId());
        if (column != null)
            row.put(column.name(), value);
        return row;
    }

    @Test
    public void testPlateUpdates() throws Exception
    {
        Plate plate = PlateManager.get().createAndSavePlate(container, user, new PlateImpl(container, null, PLATE_TYPE_12_WELL), null, null);

        // Verify updating AssayType is not allowed
        {
            var row = plateRow(plate, PlateTable.Column.AssayType, "Regular");
            assertThrows(String.format("Expected attempted update of the %s column to fail.", PlateTable.Column.AssayType.name()), QueryUpdateServiceException.class, () -> updatePlate(row));
        }

        // Verify updating PlateSet is not allowed
        {
            var row = plateRow(plate, PlateTable.Column.PlateSet, 12);
            assertThrows(String.format("Expected attempted update of the %s column to fail.", PlateTable.Column.PlateSet.name()), QueryUpdateServiceException.class, () -> updatePlate(row));
        }

        // Verify updating PlateType is not allowed
        {
            var row = plateRow(plate, PlateTable.Column.PlateType, 4);
            assertThrows(String.format("Expected attempted update of the %s column to fail.", PlateTable.Column.PlateType.name()), QueryUpdateServiceException.class, () -> updatePlate(row));
        }
    }

    @Test
    public void testTablePermissions()
    {
        verifyTablePermissions(HitTable.NAME, false, false, true);
        verifyTablePermissions(PlateSetTable.NAME, false, true, true);
        verifyTablePermissions(PlateTable.NAME, false, true, true);
        verifyTablePermissions(PlateTypeTable.NAME, false, false, false);
        verifyTablePermissions(WellGroupTable.NAME, false, false, false);
        verifyTablePermissions(WellTable.NAME, false, true, false);
    }

    private void verifyTablePermissions(String tableName, boolean allowInsert, boolean allowUpdate, boolean allowDelete)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME);
        TableInfo table = schema.getTableOrThrow(tableName);

        assertEquals(String.format("Insert permissions set incorrectly for \"%s\" table.", table.getName()), allowInsert, table.hasPermission(user, InsertPermission.class));
        assertEquals(String.format("Update permissions set incorrectly for \"%s\" table.", table.getName()), allowUpdate, table.hasPermission(user, UpdatePermission.class));
        assertEquals(String.format("Delete permissions set incorrectly for \"%s\" table.", table.getName()), allowDelete, table.hasPermission(user, DeletePermission.class));
    }

    private @NotNull Plate getPlate(int rowId)
    {
        Plate plate = PlateManager.get().getPlate(container, rowId);
        assertNotNull(String.format("Unable to get plate with RowId (%d)", rowId), plate);
        return plate;
    }

    private @NotNull Plate updatePlate(CaseInsensitiveHashMap<Object> row) throws Exception
    {
        var errors = new BatchValidationException();
        var plateRows = PlateManager.get().getPlateTable(container, user)
                .getUpdateService()
                .updateRows(user, container, Arrays.asList(row), null, errors, null, null);

        assertFalse("Expected no errors", errors.hasErrors());
        assertEquals("Expected a single row", 1, plateRows.size());

        var plateRow = plateRows.get(0);
        var plateRowId = (int) plateRow.get(PlateTable.Column.RowId.name());

        return getPlate(plateRowId);
    }
}
