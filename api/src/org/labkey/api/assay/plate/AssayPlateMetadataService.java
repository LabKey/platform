package org.labkey.api.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.qc.DataLoaderSettings;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.vfs.FileLike;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface AssayPlateMetadataService
{
    String PLATE_SET_COLUMN_NAME = "PlateSet";
    String EXPERIMENTAL_APP_PLATE_SUPPORT = "experimental-app-plate-support";

    static void setInstance(AssayPlateMetadataService serviceImpl)
    {
        ServiceRegistry.get().registerService(AssayPlateMetadataService.class, serviceImpl);
    }

    static boolean isExperimentalAppPlateEnabled()
    {
        return OptionalFeatureService.get().isFeatureEnabled(EXPERIMENTAL_APP_PLATE_SUPPORT);
    }

    static boolean isBiologicsFolder(Container container)
    {
        if (container.getProject() != null)
            return "Biologics".equals(ContainerManager.getFolderTypeName(container.getProject()));

        return false;
    }

    static AssayPlateMetadataService get()
    {
        return ServiceRegistry.get().getService(AssayPlateMetadataService.class);
    }

    /**
     * Merges the results data with the plate metadata to produce a single row map
     *
     * @return the merged rows
     */
    DataIteratorBuilder mergePlateMetadata(
        Container container,
        User user,
        Integer plateSetId,
        DataIteratorBuilder rows,
        AssayProvider provider,
        ExpProtocol protocol
    ) throws ExperimentException;

    /**
     * Handles the validation and parsing of the plate data (or data file) including plate graphical formats as
     * well as cases where plate identifiers have not been supplied.
     */
    DataIteratorBuilder parsePlateData(
        Container container,
        User user,
        @NotNull AssayRunUploadContext<?> context,
        ExpData data,
        AssayProvider provider,
        ExpProtocol protocol,
        Integer plateSetId,
        FileLike dataFile,
        DataLoaderSettings settings
    ) throws ExperimentException;

    /**
     * Returns an import helper to help join assay results data to well data and metadata that is associated
     * with the plate used in the assay run import
     */
    @NotNull
    OntologyManager.UpdateableTableImportHelper getImportHelper(
        Container container,
        User user,
        ExpRun run,
        ExpData data,
        ExpProtocol protocol,
        AssayProvider provider,
        @Nullable AssayRunUploadContext<?> context
    ) throws ExperimentException;

    /**
     * Return the domain representing the plate replicate statistical columns that are created for plate based
     * assays with replicate well groups.
     */
    @Nullable Domain getPlateReplicateStatsDomain(ExpProtocol protocol);

    /**
     * Called when a plate enabled protocol has changes to its results domain. This is to allow analogous changes
     * to the replicate table to create/delete fields to track replicate statistics.
     */
    void updateReplicateStatsDomain(
        User user,
        ExpProtocol protocol,
        GWTDomain<GWTPropertyDescriptor> update,
        Domain resultsDomain
    ) throws ExperimentException;

    /**
     * Computes and inserts replicate statistics into the protocol schema table.
     *
     * @param run The run associated with the replicate values, only required in the insert case
     * @param forInsert Boolean value to indicate insert or update of the table rows
     * @param replicateRows The assay result rows grouped by replicate well lsid.
     */
    void insertReplicateStats(
            Container container,
            User user,
            ExpProtocol protocol,
            @Nullable ExpRun run,
            boolean forInsert,
            Map<Lsid, List<Map<String, Object>>> replicateRows
    ) throws ExperimentException;

    void deleteReplicateStats(
            Container container,
            User user,
            ExpProtocol protocol,
            List<Map<String, Object>> keys
    ) throws ExperimentException;
}
