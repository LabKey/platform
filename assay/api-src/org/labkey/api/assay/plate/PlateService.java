/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.api.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;

import java.sql.SQLException;
import java.util.List;

public interface PlateService
{
    int NO_RUNID = -1;

    class NameConflictException extends Exception
    {
        public NameConflictException(String name)
        {
            super("An object with name '" + name + "' already exists.");
        }
    }

    static void setInstance(PlateService serviceImpl)
    {
        ServiceRegistry.get().registerService(PlateService.class, serviceImpl);
    }

    static PlateService get()
    {
        return ServiceRegistry.get().getService(PlateService.class);
    }

    /**
     * Instantiates a new plate instance based on the specified plate and well data.
     * This plate is not persisted to the database.
     * @param plate The plate that this instance is based upon.
     * @param wellValues A two-dimensional array of the machine data.
     * @param excludedWells A two-dimensional array of wells that are excluded (can be null)
     * @param runId Id of the run, if already exists, otherwise -1
     * @param plateNumber Plate number (1-based)
     * @return A plate instance object with the applied data.
     */
    @Nullable Plate createPlate(Plate plate, double[][] wellValues, boolean[][] excludedWells, int runId, int plateNumber);

    /**
     * Instantiates a new plate instance based on the specified plate and well data.
     * This plate is not persisted to the database.
     * @param plate The plate that this instance is based upon.
     * @param wellValues A two-dimensional array of the machine data.
     * @param excludedWells A two-dimensional array of wells that are excluded (can be null)
     * @return A plate instance object.
     */
    @Nullable Plate createPlate(Plate plate, double[][] wellValues, boolean[][] excludedWells);

    /**
     * Instantiates a new plate instance.
     * This plate is not persisted to the database.
     * @param container The template's container.
     * @param templateType The type of plate, if associated with a particular assay.
     * @param plateType Specifies the overall shape of the plate
     * @return A newly created plate instance
     * @throws IllegalArgumentException Thrown if a template of the specified name already exists in the container.
     */
    @NotNull Plate createPlate(Container container, String templateType, @NotNull PlateType plateType);

    /**
     * Instantiates a new plate template instance.
     * This plate template is not persisted to the database.
     * @param container The template's container.
     * @param templateType The type of plate template, if associated with a particular assay.
     * @param plateType Specifies the overall shape of the plate
     * @return A newly created plate template instance.
     * @throws IllegalArgumentException Thrown if a template of the specified name already exists in the container.
     */
    @NotNull Plate createPlateTemplate(Container container, String templateType, @NotNull PlateType plateType);

    /**
     * Adds a new well group to the plate
     * @param plate A plate instance object.
     * @param name The name of the well group.
     * @param type The type of well group to create.
     * @param positions A list of positions which comprises the group.
     */
    WellGroup createWellGroup(Plate plate, String name, WellGroup.Type type, List<Position> positions);

    /**
     * Gets an existing plate.
     * @param container The plate's container.
     * @param lsid The plate's lsid.
     * @return  The requested plate, or null if no plate exists with the specified name in the specified container.
     */
    @Nullable Plate getPlate(Container container, Lsid lsid);

    /**
     * Gets a plate instance object by row id.
     * @param container The plate's container.
     * @param rowId The row id of the plate.
     * @return The requested plate, or null if no plate exists with the specified row id.
     */
    @Nullable Plate getPlate(Container container, int rowId);

    /**
     * Gets a plate instance object by plate id.
     * @param container The plate's container.
     * @param plateId The plate id of the plate.
     * @return The requested plate, or null if no plate exists with the specified plate id.
     */
    @Nullable Plate getPlate(Container container, String plateId);

    /**
     * Gets a plate instance object by row id.
     * @param cf The container filter to find the plate
     * @param rowId The row id of the plate.
     * @return The requested plate, or null if no plate exists with the specified row id.
     */
    @Nullable Plate getPlate(ContainerFilter cf, int rowId);

    /**
     * Gets a plate instance object by lsid.
     * @param cf The container filter to find the plate
     * @param lsid The lsid of the plate.
     * @return The requested plate, or null if no plate exists with the specified row id.
     */
    @Nullable Plate getPlate(ContainerFilter cf, Lsid lsid);

    /**
     * Gets a plate instance object by plate id.
     * @param cf The container filter to find the plate
     * @param plateId The plate id of the plate.
     * @return The requested plate, or null if no plate exists with the specified plate id.
     */
    @Nullable Plate getPlate(ContainerFilter cf, String plateId);

    /**
     * Gets a plate instance object by plate identifier within a given plate set.
     * @param cf The container filter to find the plate
     * @param plateSetId The plate set id.
     * @param plateIdentifier The plate rowId, plateId, or name (checked in that order).
     * @return The requested plate, or null if no plate exists with the specified plate identifier.
     */
    @Nullable Plate getPlate(ContainerFilter cf, Integer plateSetId, Object plateIdentifier);

    /**
     * Gets all plate templates for the specified container. Plate templates are Plate instances
     * which have their template field set to TRUE.
     *
     * @return An array of all plates that are configured as templates from the specified container.
     */
    @NotNull
    List<Plate> getPlateTemplates(Container container);

    /**
     * Gets the plate set by ID
     *
     * @return A plate set instance or null if it can't be located
     */
    @Nullable PlateSet getPlateSet(Container container, int plateSetId);

    @Nullable PlateSet getPlateSet(ContainerFilter cf, int plateSetId);

    /**
     * Returns the list of available plate types.
     */
    @NotNull List<? extends PlateType> getPlateTypes();

    /**
     * Returns the plate type matching the specified shape.
     */
    @Nullable PlateType getPlateType(int rows, int columns);

    /**
     * Returns the number of assay runs that are linked to the specified plate. Currently, this only works
     * for the standard assay with plate support since other assays types do not store the plate ID with the
     * run.
     */
    int getRunCountUsingPlate(@NotNull Container c, @NotNull User user, @NotNull Plate plate);

    List<? extends ExpRun> getRunsUsingPlate(@NotNull Container c, @NotNull User user, @NotNull Plate plate);

    /**
     * Creates a new plate template.
     * @param container The template's container.
     * @param user The current user.
     * @param plate The plate template to save.
     * @return A newly created plate template instance.
     * @throws SQLException Thrown in the event of a database failure.
     * @throws IllegalArgumentException Thrown if a template of the specified name already exists in the container.
     */
    int save(Container container, User user, Plate plate) throws Exception;

    /**
     * Gets a well group by row id.
     * @param container The object's container.
     * @param rowid The row id of the well group.
     * @return The requested well group, or null if no well group exists with the specified row id.
     */
    WellGroup getWellGroup(Container container, int rowid);

    /**
     * Creates a position object.
     * @param container The object's container.
     * @param row The position's row.
     * @param column The position's column.
     * @return A newly created position.
     */
    Position createPosition(Container container, int row, int column);

    /**
     * Deletes all plate and template data from the specified container.  Typically used
     * only when a container is deleted.
     * @param container The container from which to delete all plate data.
     */
    void deleteAllPlateData(Container container);

    /**
     * Deletes a single plate object.
     * @param container The object's container.
     * @param rowid The row id of the plate object to be deleted.
     */
    void deletePlate(Container container, User user, int rowid) throws Exception;

    /**
     * Registration of a details link provider allows plate grid views to include links to plate-specific detail pages.
     * @param resolver A resolver implementation that will return details URLs for recognized plate instances.
     */
    void registerDetailsLinkResolver(PlateDetailsResolver resolver);

    /**
     * Registers a handler for a particular type of plate
     */
    void registerPlateLayoutHandler(PlateLayoutHandler handler);

    /**
     * Calculates a dilution curve for the specified well groups.
     * @param wellGroups The well groups containing WellData objects over which a curve should be calculated. WellData objects from these
     * groups will be traversed in the provided group order.
     * @param assumeDecreasing Whether the curve is assumed to be decreasing by default.  Used only if the data points are too chaotic to provide a reasonable guess.
     * @param percentCalculator A callback to allow the caller to determine the plottable value for a given WellData within its WellGroup.
     * @param type The Type of fit desired.
     * @return A DilutionCurve instance of the appropriate type, if a fit was possible.
     * @throws FitFailedException Thrown if a curve cannot be fit to the data points.
     */
    DilutionCurve getDilutionCurve(List<WellGroup> wellGroups, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator, StatsService.CurveFitType type) throws FitFailedException;

    /**
     * Retrieve the TableInfo for the Plate table.
     */
    TableInfo getPlateTableInfo();

    /**
     * Create the plate metadata domain for this container.
     */
    @NotNull Domain ensurePlateMetadataDomain(Container container, User user) throws ValidationException;

    /**
     * Name expressions for plate sets and plates.
     */
    @NotNull String getPlateSetNameExpression();
    @NotNull String getPlateNameExpression();

    /**
     * A PlateDetailsResolver implementation provides a URL where a detailed, plate-type specific
     * UI can be found.
     */
    interface PlateDetailsResolver
    {
        /**
         * Returns a URL to the details page for this plate.
         * @param plate The plate instance for which a details URL is desired.
         * @return A valid and complete ActionURL if the plate is recognized, or null if it is not.
         */
        ActionURL getDetailsURL(Plate plate);
    }
}
