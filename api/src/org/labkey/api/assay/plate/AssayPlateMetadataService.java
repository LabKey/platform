package org.labkey.api.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.assay.AssayDataType;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.settings.ExperimentalFeatureService;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface AssayPlateMetadataService
{
    String PLATE_TEMPLATE_COLUMN_NAME = "PlateTemplate";
    String PLATE_SET_COLUMN_NAME = "PlateSet";
    Map<AssayDataType, AssayPlateMetadataService> _handlers = new HashMap<>();
    String EXPERIMENTAL_APP_PLATE_SUPPORT = "experimental-app-plate-support";

    static void registerService(AssayDataType dataType, AssayPlateMetadataService handler)
    {
        if (dataType != null)
        {
            _handlers.put(dataType, handler);
        }
        else
            throw new RuntimeException("The specified assay data type is null");
    }

    static boolean isExperimentalAppPlateEnabled()
    {
        return ExperimentalFeatureService.get().isFeatureEnabled(EXPERIMENTAL_APP_PLATE_SUPPORT);
    }

    @Nullable
    static AssayPlateMetadataService getService(AssayDataType dataType)
    {
        return _handlers.get(dataType);
    }

    /**
     * Return the domain representing the plate metadata columns that are created at data import time for the
     * assay protocol. The domain is populated from the properties parsed from the plate metadata JSON file.
     *
     * @return the Domain or null if no import has been performed yet
     */
    @Nullable Domain getPlateDataDomain(ExpProtocol protocol);

    /**
     * Adds plate metadata to the run, this is called as part of AssayRunCreator.saveExperimentRun and after the
     * result data has been imported
     *
     * @param plateMetadata the parsed plate metadata, use the parsePlateMetadata methods to convert from File or JSON objects.
     * @param inserted the inserted result data
     * @param rowIdToLsidMap a map of result data rowIds to result data lsids
     */
    void addAssayPlateMetadata(ExpData resultData, Map<String, MetadataLayer> plateMetadata, Container container, User user, ExpRun run, AssayProvider provider,
                               ExpProtocol protocol, List<Map<String, Object>> inserted, Map<Integer, String> rowIdToLsidMap) throws ExperimentException;

    /**
     * Merges the results data with the plate metadata to produce a single row map
     *
     * @return the merged rows
     */
    List<Map<String, Object>> mergePlateMetadata(Container container, User user, Lsid plateLsid, Integer plateSetId,
                                                 List<Map<String, Object>> rows, @Nullable Map<String, MetadataLayer> plateMetadata,
                                                 AssayProvider provider, ExpProtocol protocol) throws ExperimentException;

    /**
     * Methods to create the metadata model from either a JSON object or a file object
     */
    Map<String, MetadataLayer> parsePlateMetadata(JSONObject json) throws ExperimentException;
    Map<String, MetadataLayer> parsePlateMetadata(File jsonData) throws ExperimentException;

    /**
     * Returns an import helper to help join assay results data to well data and metadata that is associated
     * with the plate used in the assay run import
     */
    @NotNull
    OntologyManager.UpdateableTableImportHelper getImportHelper(Container container, User user, ExpRun run, ExpData data, ExpProtocol protocol, AssayProvider provider) throws ExperimentException;

    interface MetadataLayer
    {
        // the name of this layer
        String getName();

        // returns the well groups for this layer
        Map<String, MetadataWellGroup> getWellGroups();
    }

    interface MetadataWellGroup
    {
        // the name of this well group
        String getName();

        // returns the properties associated with this well group
        Map<String, Object> getProperties();
    }
}
