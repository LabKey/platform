package org.labkey.api.study;

import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.attachments.AttachmentService;
import org.apache.struts.upload.FormFile;

import java.sql.SQLException;
import java.io.IOException;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 10:12:22 AM
 */
public class PlateService
{
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
         * @return A plate instance object.
         */
        Plate createPlate(PlateTemplate template, double[][] wellValues);

        /**
         * Gets an existing plate template.
         * @param container The template's container.
         * @param templateName The template's name.
         * @return  The requested plate template, or null if no template exists with the specified name in the specified container.
         * @throws SQLException Thrown in the event of a database failure.
         */
        PlateTemplate getPlateTemplate(Container container, String templateName) throws SQLException;

        /**
         * Gets all plate templates for the specified container.
         * @param container The templates' container.
         * @return An array of all plate templates from the specified container.
         * @throws SQLException Thrown in the event of a database failure.
         */
        PlateTemplate[] getPlateTemplates(Container container) throws SQLException;

        /**
         * Creates a new plate template.
         * @param container The template's container.
         * @return A newly created plate template instance.
         * @throws SQLException Thrown in the event of a database failure.
         * @throws IllegalArgumentException Thrown if a template of the specified name already exists in the container.
         */
        PlateTemplate createPlateTemplate(Container container, String templateType) throws SQLException;

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
         * @throws SQLException Thrown in the event of a database failure.
         */
        Plate getPlate(Container container, int rowid) throws SQLException;

        /**
         * Gets a plate instance by entity id.
         * @param container The plate's container.
         * @param entityId The plate's entity id.
         * @return The requested plate, or null if no plate exists with the specified entity id.
         * @throws SQLException Thrown in the event of a database failure.
         */
        Plate getPlate(Container container, String entityId) throws SQLException;

        /**
         * Gets a well group by row id.
         * @param container The object's container.
         * @param rowid The row id of the well group.
         * @return The requested well group, or null if no well group exists with the specified row id.
         * @throws SQLException Thrown in the event of a database failure.
         */
        WellGroup getWellGroup(Container container, int rowid) throws SQLException;

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
         * @throws SQLException Thrown in the event of a database failure.
         */
        void deleteAllPlateData(Container container) throws SQLException;

        /**
         * Deletes a single plate object.
         * @param container The object's container.
         * @param rowid The row id of the plate object to be deleted.
         * @throws SQLException Thrown in the event of a database failure.
         */
        void deletePlate(Container container, int rowid) throws SQLException;

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
         * @throws SQLException Thrown in the event of a database failure.
         * @throws IOException Thrown if the specified file cannot be read.
         * @throws org.labkey.api.attachments.AttachmentService.DuplicateFilenameException Thrown if the specified file has already been attached to this plate
         */
        void setDataFile(User user, Plate plate, FormFile file) throws SQLException, IOException, AttachmentService.DuplicateFilenameException;

        /**
         * Deletes a plate-associated data file.
         * @param plate The plate associated with the data file to be deleted.
         * @throws SQLException Thrown in the event of a database failure.
         */
        void deleteDataFile(Plate plate) throws SQLException;

        /**
         * Gets the download URL for the associated plate's data file.
         * @param plate The plate associated with the data file to be downloaded.
         * @param pageFlow The pageflow that contains the 'download' action.
         * @return A ActionURL containing the download URL.
         */
        ActionURL getDataFileURL(Plate plate, String pageFlow);

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
