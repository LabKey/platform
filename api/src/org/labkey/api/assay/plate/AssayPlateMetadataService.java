package org.labkey.api.assay.plate;

import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.assay.AssayDataType;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface AssayPlateMetadataService
{
    Map<AssayDataType, AssayPlateMetadataService> _handlers = new HashMap<>();

    static void registerService(AssayDataType dataType, AssayPlateMetadataService handler)
    {
        if (dataType != null)
        {
            _handlers.put(dataType, handler);
        }
        else
            throw new RuntimeException("The specified assay data type is null");
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
     * Methods to create the metadata model from either a JSON object or a file object
     */
    Map<String, MetadataLayer> parsePlateMetadata(JSONObject json) throws ExperimentException;
    Map<String, MetadataLayer> parsePlateMetadata(File jsonData) throws ExperimentException;

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
