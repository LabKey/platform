package org.labkey.study.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GetDatasetColumnsTool implements ClaudeTool
{
    @Override
    public String getName()
    {
        return "get_dataset_columns";
    }

    @Override
    public String getDescription()
    {
        return "Returns column metadata for study datasets including name, label, type, nullability, " +
                "analytical properties (measure/dimension), and lookup info. When called with a specific datasetName, " +
                "returns additional detail per column such as description and format strings. " +
                "Use get_datasets first to discover available dataset names.";
    }

    @Override
    public JSONObject getInputSchema()
    {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");

        JSONObject properties = new JSONObject();
        JSONObject datasetNameProp = new JSONObject();
        datasetNameProp.put("type", "string");
        datasetNameProp.put("description", "Optional dataset name to get columns for a specific dataset. If omitted, returns columns for all datasets.");
        properties.put("datasetName", datasetNameProp);
        schema.put("properties", properties);

        return schema;
    }

    @Override
    public JSONArray execute(User user, Container container, JSONObject input)
    {
        JSONArray resultArray = new JSONArray();
        UserSchema studySchema = QueryService.get().getUserSchema(user, container, "study");
        if (studySchema == null)
            return resultArray;

        String requestedDataset = input != null && input.has("datasetName") ? input.getString("datasetName") : null;
        boolean singleDataset = requestedDataset != null && !requestedDataset.isEmpty();

        List<String> datasetNames;
        if (singleDataset)
        {
            datasetNames = Collections.singletonList(requestedDataset);
        }
        else
        {
            datasetNames = getDatasetNames(studySchema);
        }

        for (String datasetName : datasetNames)
        {
            TableInfo tableInfo = studySchema.getTable(datasetName);
            if (tableInfo == null)
                continue;

            for (ColumnInfo col : tableInfo.getColumns())
            {
                // In all-datasets mode, skip hidden columns to reduce output size
                if (!singleDataset && col.isHidden())
                    continue;

                JSONObject colJson = new JSONObject();
                colJson.put("dataset", datasetName);
                colJson.put("name", col.getName());
                colJson.put("label", col.getLabel());
                colJson.put("friendlyType", col.getFriendlyTypeName());
                colJson.put("jsonType", col.getJdbcType().json);
                colJson.put("nullable", col.isNullable());
                colJson.put("hidden", col.isHidden());
                colJson.put("keyField", col.isKeyField());
                colJson.put("measure", col.isMeasure());
                colJson.put("dimension", col.isDimension());

                ForeignKey fk = col.getFk();
                if (fk != null)
                {
                    JSONObject lookupJson = new JSONObject();
                    if (fk.getLookupSchemaKey() != null)
                        lookupJson.put("schema", fk.getLookupSchemaKey().toString());
                    if (fk.getLookupTableName() != null)
                        lookupJson.put("query", fk.getLookupTableName());
                    if (fk.getLookupDisplayName() != null)
                        lookupJson.put("displayColumn", fk.getLookupDisplayName().isEmpty() ? fk.getLookupColumnName() : fk.getLookupDisplayName());
                    if (lookupJson.length() > 0)
                        colJson.put("lookup", lookupJson);
                }

                // Include verbose fields only in single-dataset mode
                if (singleDataset)
                {
                    String description = col.getDescription();
                    if (description != null && !description.isEmpty())
                        colJson.put("description", description);

                    String format = col.getFormat();
                    if (format != null && !format.isEmpty())
                        colJson.put("format", format);
                }

                resultArray.put(colJson);
            }
        }

        return resultArray;
    }

    private List<String> getDatasetNames(UserSchema studySchema)
    {
        List<String> names = new ArrayList<>();
        TableInfo datasetsTable = studySchema.getTable("Datasets");
        if (datasetsTable != null)
        {
            TableSelector ts = new TableSelector(datasetsTable, PageFlowUtil.set("Name"), null, null);
            ts.forEach(rs -> names.add(rs.getString("Name")));
        }
        return names;
    }
}
