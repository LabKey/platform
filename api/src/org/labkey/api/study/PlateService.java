/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.api.study;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.web.servlet.mvc.Controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 10:12:22 AM
 */
public class PlateService
{
    public static final int NO_RUNID = -1;

    private static Service _serviceImpl = null;

    /**
     * A PlateDetailsResolver implementation provides a URL where a detailed, plate-type specific
     * UI can be found.
     */
    public interface PlateDetailsResolver
    {
        /**
         * Returns a URL to the a details page for this plate.
         * @param plate The plate instance for which a details URL is desired.
         * @return A valid and complete ActionURL if the plate is recognized, or null if it is not.
         */
        ActionURL getDetailsURL(Plate plate);
    }

    public interface Service
    {
        /**
         * Creates a new plate instance based on the specified plate template and well data.
         * @param template The template that this instance is based upon.
         * @param wellValues A two-dimentional arary of the machine data.
         * @param runId Id of the run, if already exists, otherwise -1
         * @param plateNumber Plate number (1-based)
         * @return A plate instance object.
         */
        Plate createPlate(PlateTemplate template, double[][] wellValues, int runId, int plateNumber);

        /**
         * Creates a new plate instance based on the specified plate template and well data.
         * @param template The template that this instance is based upon.
         * @param wellValues A two-dimentional arary of the machine data.
         * @return A plate instance object.
         */
        Plate createPlate(PlateTemplate template, double[][] wellValues);

        /**
         * Adds a new well group to the plate
         * @param plate A plate instance object.
         * @param name The name of the well group.
         * @param type The type of well group to create.
         * @param positions A list of positions which comprises the group.
         * @return
         */
        WellGroup createWellGroup(Plate plate, String name, WellGroup.Type type, List<Position> positions);

        /**
         * Gets an existing plate template.
         * @param container The template's container.
         * @param templateName The template's name.
         * @return  The requested plate template, or null if no template exists with the specified name in the specified container.
         */
        PlateTemplate getPlateTemplate(Container container, String templateName);

        /**
         * Gets an existing plate template.
         * @param container The template's container.
         * @param plateId The template's id.
         * @return  The requested plate template, or null if no template exists with the specified name in the specified container.
         */
        PlateTemplate getPlateTemplate(Container container, int plateId);

        /**
         * Gets all plate templates for the specified container.
         * @param container The templates' container.
         * @return An array of all plate templates from the specified container.
         */
        @NotNull
        List<? extends PlateTemplate> getPlateTemplates(Container container);

        /**
         * Creates a new plate template.
         * @param container The template's container.
         * @param templateType The type of plate template, if associated with a particular assay.
         * @param rowCount The number of columns in the plate.
         * @param columnCount The number of rows in the plate.
         * @return A newly created plate template instance.
         * @throws SQLException Thrown in the event of a database failure.
         * @throws IllegalArgumentException Thrown if a template of the specified name already exists in the container.
         */
        PlateTemplate createPlateTemplate(Container container, String templateType, int rowCount, int columnCount) throws SQLException;

        /**
         * Creates a new plate template.
         * @param container The template's container.
         * @param user The current user.
         * @param plate The plate template to save.
         * @return A newly created plate template instance.
         * @throws SQLException Thrown in the event of a database failure.
         * @throws IllegalArgumentException Thrown if a template of the specified name already exists in the container.
         */

        int save(Container container, User user, PlateTemplate plate) throws SQLException;

        /**
         * Gets a plate instance object by row id.
         * @param container The plate's container.
         * @param rowid The row id of the plate.
         * @return The requested plate, or null if no plate exists with the specified row id.
         */
        Plate getPlate(Container container, int rowid);

        /**
         * Gets a plate instance by entity id.
         * @param container The plate's container.
         * @param entityId The plate's entity id.
         * @return The requested plate, or null if no plate exists with the specified entity id.
         */
        Plate getPlate(Container container, String entityId);

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
         * Creates a new grid view of all plates created from the specified template.
         * @param context The view context into which the grid view will be rendered.
         * @return Returns a query view that may be used in UI.
         */
        PlateQueryView getPlateGridView(ViewContext context);

        /**
         * Creates a new grid view of all plates created from the specified template.
         *
         * @param context The view context into which the grid view will be rendered.
         * @param filter The filter to apply to the plate view.
         * @return Returns a query view that may be used in UI.
         */
        PlateQueryView getPlateGridView(ViewContext context, SimpleFilter filter);

        /**
         * Creates a new grid view of all well groups created from the specified template.
         * @param context The view context into which the grid view will be rendered.
         * @return Returns a query view that may be used in UI.
         */
        PlateQueryView getWellGroupGridView(ViewContext context);

        /**
         * Creates a new grid view of all well groups of a certain type created from the specified template.
         * @param context The view context into which the grid view will be rendered.
         * @param showOnlyType The well group type that should be included in the grid view.
         * @return Returns a query view that may be used in UI.
         */
        PlateQueryView getWellGroupGridView(ViewContext context, WellGroup.Type showOnlyType);

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
        void deletePlate(Container container, int rowid);

        /**
         * Registration of a details link provider allows plate grid views to include links to plate-specific detail pages.
         * @param resolver A resolver implementation that will return details URLs for recognized plate instances.
         */
        void registerDetailsLinkResolver(PlateDetailsResolver resolver);

        /**
         * Associates a data file with a plate.  Only one file may be associated with a given plate.
         * @param user The current user.
         * @param plate The plate with which to associate the data file.
         * @param file The file to associate.
         * @throws IOException Thrown if the specified file cannot be read.
         * @throws org.labkey.api.attachments.AttachmentService.DuplicateFilenameException Thrown if the specified file has already been attached to this plate
         */
        void setDataFile(User user, Plate plate, AttachmentFile file) throws IOException, AttachmentService.DuplicateFilenameException;

        /**
         * Deletes a plate-associated data file.
         * @param plate The plate associated with the data file to be deleted.
         */
        void deleteDataFile(Plate plate);

        /**
         * Gets the download URL for the associated plate's data file.
         * @param plate The plate associated with the data file to be downloaded.
         * @param downloadClass The controller class that implements the 'download' action.
         * @return A ActionURL containing the download URL.
         */
        ActionURL getDataFileURL(Plate plate, Class<? extends Controller> downloadClass);

        /**
         * Copies a plate template from one container to another.
         * @param source The source plate template
         * @param user The user performing the copy
         * @param destination The destinatino container
         * @return The copied plate template
         * @throws SQLException Thrown in the event of a database failure.
         * @throws NameConflictException Thrown if the destination container already contains a template by the same name.
         */
        PlateTemplate copyPlateTemplate(PlateTemplate source, User user, Container destination) throws SQLException, NameConflictException;


        /**
         * Registers a handler for a particular type of plate
         */
        void registerPlateTypeHandler(PlateTypeHandler handler);

        /**
         * Calculates a dilution curve for the specified well group.
         * @param wellGroup The well group containing WellData objects over which a curve should be calculated.
         * @param assumeDecreasing Whether the curve is assumed to be decreasing by default.  Used only if the data points are too chaotic to provide a reasonable guess.
         * @param percentCalculator A callback to allow the caller to determine the plottable value for a given WellData within its WellGroup.
         * @param type The Type of fit desired.
         * @return A DilutionCurve instance of the appropriate type, if a fit was possible.
         * @throws org.labkey.api.data.statistics.FitFailedException Thrown if a curve cannot be fit to the data points.
         */
        DilutionCurve getDilutionCurve(WellGroup wellGroup, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator, StatsService.CurveFitType type) throws FitFailedException;

        /**
         * Calculates a dilution curve for the specified well groups.
         * @param wellGroups The well groups containing WellData objects over which a curve should be calculated. WellData objects from these
         * groups will be traversed in the provided group order.
         * @param assumeDecreasing Whether the curve is assumed to be decreasing by default.  Used only if the data points are too chaotic to provide a reasonable guess.
         * @param percentCalculator A callback to allow the caller to determine the plottable value for a given WellData within its WellGroup.
         * @param type The Type of fit desired.
         * @return A DilutionCurve instance of the appropriate type, if a fit was possible.
         * @throws org.labkey.api.data.statistics.FitFailedException Thrown if a curve cannot be fit to the data points.
         */
        DilutionCurve getDilutionCurve(List<WellGroup> wellGroups, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator, StatsService.CurveFitType type) throws FitFailedException;
    }

    public static class NameConflictException extends Exception
    {
        public NameConflictException(String name)
        {
            super("An object with name '" + name + "' already exists.");
        }
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }
}
