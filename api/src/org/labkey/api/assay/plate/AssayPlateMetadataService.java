package org.labkey.api.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
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
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.Role;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.vfs.FileLike;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface AssayPlateMetadataService
{
    String PLATE_SET_COLUMN_NAME = "PlateSet";
    String HIT_SELECTION_CRITERIA_COLUMN_NAME = "HitSelectionCriteria";

    static void setInstance(AssayPlateMetadataService serviceImpl)
    {
        ServiceRegistry.get().registerService(AssayPlateMetadataService.class, serviceImpl);
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

    Map<String, List<GWTPropertyDescriptor>> previewFilterCriteriaColumns(@NotNull ExpProtocol protocol, List<String> columnNames);
    Map<String, List<GWTPropertyDescriptor>> previewFilterCriteriaColumns(@NotNull Container container, String protocolName, List<String> columnNames);

    /**
     * Merges the results data with the plate metadata to produce a single row map
     *
     * @return the merged rows
     */
    DataIteratorBuilder mergePlateMetadata(
        Container container,
        User user,
        Long plateSetId,
        DataIteratorBuilder rows,
        AssayProvider provider,
        ExpProtocol protocol
    ) throws ExperimentException;

    /**
     * Takes the current incoming data and combines it with any data uploaded in the previous run (re-run ID). Data
     * can be combined for plates within a plate set, but only on a per plate boundary. If there is data for plates
     * in both sets of data, the most recent data will take precedence.
     *
     * @param results The incoming data rows
     * @return The new, combined data
     */
    DataIteratorBuilder mergeReRunData(
        Container container,
        User user,
        @NotNull AssayRunUploadContext<?> context,
        DataIterator results,
        AssayProvider provider,
        ExpProtocol protocol,
        ExpData data
    ) throws ExperimentException;

    /**
     * Returns the plate set ID for the current run context.
     */
    @Nullable
    Long getPlateSetId(
        AssayRunUploadContext<?> context,
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
        Long plateSetId,
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
    );

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
        GWTDomain<GWTPropertyDescriptor> original,
        GWTDomain<GWTPropertyDescriptor> update
    ) throws ValidationException;

    /**
     * Computes and inserts replicate statistics into the protocol schema table.
     *
     * @param run The run associated with the replicate values
     * @param replicateRows The assay result rows grouped by replicate well lsid.
     */
    void insertReplicateStats(
        Container container,
        User user,
        ExpProtocol protocol,
        @NotNull ExpRun run,
        Map<Lsid, List<Map<String, Object>>> replicateRows
    ) throws ValidationException;

    void updateReplicateStats(
        Container container,
        User user,
        ExpProtocol protocol,
        Map<Lsid, List<Map<String, Object>>> replicateRows
    ) throws ValidationException;

    void deleteReplicateStats(
        Container container,
        User user,
        ExpProtocol protocol,
        List<Map<String, Object>> keys
    ) throws ValidationException;

    void applyHitSelectionCriteria(
        Container container,
        User user,
        ExpProtocol protocol,
        TableInfo resultsTable,
        List<Long> runIds
    ) throws ValidationException;

    /**
     * Returns a Map of Well Location to Sample RowID for a given Plate ID.
     */
    Map<String, Long> getWellLocationToSampleIdMap(Long plateId);

    boolean isWellLookup(ColumnInfo col);

    /**
     * Should only be used to get a local instance of a plate schema where a contextual role might be involved. Schemas created this way are not cached,
     * and all other usages should retrieve schemas from the QueryService.
     * <p>
     * This method would be better placed in the PlateService interface, but it would have required moving that (and other) classes into API.
     */
    @NotNull
    UserSchema getPlateSchema(QuerySchema schema, Set<Role> contextualRoles);
}
