package org.labkey.redcap;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.xmlbeans.XmlObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Type;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.writer.DefaultContainerUser;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.MemoryVirtualFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.study.xml.DatasetsDocument;
import org.labkey.study.xml.ExportDirType;
import org.labkey.study.xml.SecurityType;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.TimepointType;
import org.labkey.study.xml.VisitMapDocument;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 3/11/2015.
 */
public class RedcapExport
{
    private static final String DEFAULT_DIRECTORY = "datasets";
    private static final String LISTS_DIRECTORY = "lists";
    private static final String MANIFEST_FILENAME = "datasets_manifest.xml";
    private static final String SCHEMA_FILENAME = "datasets_metadata.xml";
    private static final String VISIT_FILENAME = "visit_map.xml";

    private static final String LOOKUP_KEY_FIELD = "key";
    private static final String LOOKUP_NAME_FIELD = "name";
    private static final String REDCAP_EVENT_COL_NAME = "redcap_event_name";

    private RedcapConfiguration _config;
    private Map<String, Double> _visitToSequenceNumber = new HashMap<String, Double>();
    private Map<String, List<Map<String, Object>>> _lookups = new HashMap<>();
    private Map<String, ColumnInfo> _columnInfoMap = new HashMap<>();
    private Map<String, ColumnInfo> _multiValueColumns = new HashMap<>();
    private Map<String, Integer> _datasetNameToId = new HashMap<>();
    private Map<String, String> _subjectIdFieldMap = new HashMap<>();
    private boolean _createDefaultTimepoint;
    private Set<String> _mergedDatasets = new HashSet<>();
    private PipelineJob _job;

    public enum LogLevel {
        INFO,
        DEBUG,
        WARN,
        ERROR,
    };

    public RedcapExport()
    {
    }

    public RedcapExport(PipelineJob job)
    {
        _job = job;

        RedcapManager.RedcapSettings settings = RedcapManager.getRedcapSettings(new DefaultContainerUser(job.getContainer(), job.getUser()));
        if (settings != null)
            _config = new RedcapConfiguration(job, settings);
        else
            job.error("Unable to create a valid REDCap configuration object");
    }

    public void exportProject()
    {
        _job.info("Starting REDCap export");
        HttpClientBuilder builder = HttpClientBuilder.create();
        try
        {
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContextBuilder.build());
            builder.setSSLSocketFactory(sslConnectionSocketFactory);
        }
        catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e)
        {
            _job.error("Error creating SSL Context", e);
            throw new RuntimeException(e);
        }
        HttpClient client = builder.build();

        try
        {
            Map<String, Map<String, List<ColumnInfo>>> metadata = getDatasetMetadata(client);
            if (!metadata.isEmpty())
            {
                VirtualFile vf = new FileSystemFile(_job.getPipeRoot().getRootPath());

                // for now, always unzipping into the pipeline root
/*
                if (_config.isUnzipContents())
                    vf = new FileSystemFile(_config.getArchiveFile());
                else
                    vf = new ZipFile(_config.getArchiveFile().getParentFile(), _config.getArchiveFile().getName());
*/

                // create the study.xml
                _job.info("Creating study archive");
                writeStudy(vf);

                // write the dataset metadata
                _job.info("Writing dataset metadata");
                writeDatasetMetadata(vf.getDir(DEFAULT_DIRECTORY), metadata);

                // add any lookup data
                if (!_lookups.isEmpty())
                {
                    _job.info("Creating column lookups");
                    writeLookups(vf.getDir(LISTS_DIRECTORY));
                }

                // export any event information (if configured)
                List<Map<String, Object>> visits = Collections.emptyList();
                if (_config.getTimepointType().equals(TimepointType.VISIT))
                {
                    _job.info("Creating visit map");
                    visits = getVisits(client);
                    writeVisits(vf, visits);
                }

                // loop through all of the configured projects and collect the tsv data
                Map<String, List<Map<String, Object>>> datasetData = new HashMap<>();
                for (RedcapConfiguration.RedcapProject project : _config.getProjects())
                {
                    String token = _config.getToken(project.getProjectName());
                    if (token != null)
                    {
                        ExportDataCommand exportDataCmd = new ExportDataCommand(project.getServerUrl(), token);
                        RedcapCommandResponse response = exportDataCmd.execute(client);

                        if (response.getStatusCode() == HttpStatus.SC_OK)
                        {
                            List<Map<String, Object>> data = parseDatasetData(response.getLoader());
                            Map<String, List<ColumnInfo>> projMetadata = metadata.get(project.getProjectName());
                            createDatasetData(datasetData, project, projMetadata, data);
                        }
                        else
                            _job.error("Error trying to export data, status code : " + response.getStatusCode());
                    }
                    else
                        _job.error("Could not find _netrc entry for host : " + project.getServerUrl() + " and project : " + project.getProjectName());
                }

                // serialize to the individual tsv files
                writeDatasetData(datasetData, vf.getDir(DEFAULT_DIRECTORY));

                if (_config.isUnzipContents())
                {
                    try (PrintWriter pw = vf.getPrintWriter("studyload.txt"))
                    {
                        Date date = new Date();
                        pw.write(date.toString());
                    }
                }

                vf.close();
                _job.info("Finished creating study archive");
            }
        }
        catch (Exception e)
        {
            _job.error("An error occurred importing REDCap", e);
        }
    }

    /**
     * Loop through all the configured projects exporting and parsing the metadata into individual
     * datasets that will be used to create the study archive
     */
    private Map<String, Map<String, List<ColumnInfo>>> getDatasetMetadata(HttpClient client) throws Exception
    {
        Map<String, Map<String, List<ColumnInfo>>> projectDatasets = new HashMap<>();
        Map<String, List<ColumnInfo>> allDatasets = new HashMap<>();

        for (RedcapConfiguration.RedcapProject project : _config.getProjects())
        {
            String token = _config.getToken(project.getProjectName());
            if (token != null)
            {
                ExportMetadataCommand exportMetadataCmd = new ExportMetadataCommand(project.getServerUrl(), token);
                RedcapCommandResponse response = exportMetadataCmd.execute(client);

                if (response != null && response.getStatusCode() == HttpStatus.SC_OK)
                {
                    Map<String, List<ColumnInfo>> projectMetadata = new LinkedHashMap<>();
                    for (Map.Entry<String, List<ColumnInfo>> ds : parsetMetadata(project, response.getLoader()).entrySet())
                    {
                        // check for dataset name collisions between redcap projects, for now just error out but in the
                        // future we could try to uniquify the name
                        //
                        //String dsName = getUniqueName(ds.getKey(), allDatasets);
                        if (!allDatasets.containsKey(ds.getKey()))
                        {
                            allDatasets.put(ds.getKey(), ds.getValue());
                            projectMetadata.put(ds.getKey(), ds.getValue());
                        }
                        else
                        {
                            switch (_config.getDuplicateNamePolicy())
                            {
                                case merge:
                                    // verify that any duplicates have the same schema shape
                                    verifyDuplicateDataset(project.getProjectName(), ds.getKey(), allDatasets.get(ds.getKey()), ds.getValue());

                                    // add a copy of the common metadata to this individual project
                                    projectMetadata.put(ds.getKey(), allDatasets.get(ds.getKey()));
                                    setDatasetMerged(project.getProjectName(), ds.getKey());
                                    break;
                                case fail:
                                    String message = "Collection instrument name collision found in REDCap project : " + project.getProjectName() + " (" + ds.getKey() + ") please " +
                                            "ensure that all projects in this configuration have unique collection instrument names.";
                                    _job.error(message);
                                    throw new RuntimeException(message);
                            }
                        }
                    }
                    projectDatasets.put(project.getProjectName(), projectMetadata);
                }
                else
                    _job.error("Error trying to export metadata, status code : " + response.getStatusCode());
            }
            else
                _job.error("Could not find _netrc entry for host : " + project.getServerUrl() + " and project : " + project.getProjectName());
        }
        return projectDatasets;
    }

    private void setDatasetMerged(String projectName, String datasetName)
    {
        String key = projectName + "~" + datasetName;
        if (_mergedDatasets.contains(key))
        {
            String message = "The dataset: " + datasetName + " for project: " + projectName + " is already marked as merged.";
            _job.error(message);
            throw new RuntimeException(message);
        }
        _mergedDatasets.add(key);
    }

    private boolean isDatasetMerged(String projectName, String datasetName)
    {
        String key = projectName + "~" + datasetName;
        return _mergedDatasets.contains(key);
    }

    private void verifyDuplicateDataset(String projectName, String datasetName, List<ColumnInfo> prevDataset, List<ColumnInfo> newDataset)
    {
        String errorMessage = null;
        Map<String, ColumnInfo> columnInfoMap = new HashMap<>();
        for (ColumnInfo col : prevDataset)
            columnInfoMap.put(col.getName(), col);

        for (ColumnInfo col : newDataset)
        {
            ColumnInfo oldCol = columnInfoMap.get(col.getName());

            if (oldCol != null)
            {
                if (!oldCol.getJdbcType().equals(col.getJdbcType()))
                {
                    errorMessage = "Attempting to merge the dataset : " + datasetName + " for project : " + projectName + " failed because the dataset schemas had a different set of columns. The " +
                            "column : " + col.getName() + " were different JDBC types.";
                    break;
                }
            }
            else
            {
                errorMessage = "Attempting to merge the dataset : " + datasetName + " for project : " + projectName + " failed because the dataset schemas had a different set of columns. Unable " +
                        "to find columns : " + col.getName();
                break;
            }
        }

        if (errorMessage != null)
        {
            _job.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
    }

    /**
     * Creates the unique dataset name
     * @param name
     * @param allDatsets
     * @return
     */
    private String getUniqueName(String name, Set<String> allDatsets)
    {
        if (!allDatsets.contains(name))
            return name;
        else
        {
            _job.warn("REDCap collection instrument name collision detected, attempting to resolve : " + name);
            for (int i = 1; i < 100; i++)
            {
                String newName = String.format("%s_%03d", name, i);
                if (!allDatsets.contains(newName))
                    return newName;
            }
        }
        String message = "Unable to generate a unique name for REDCap collection instrument: " + name;
        _job.error(message);
        throw new RuntimeException(message);
    }

    /**
     * Parses the redcap export response to create the datasets representation of the redcap project
     * @param loader
     * @return
     */
    private Map<String, List<ColumnInfo>> parsetMetadata(RedcapConfiguration.RedcapProject project, DataLoader loader)
    {
        try {
            List<Map<String, Object>> rows = loader.load();

            Map<String, List<ColumnInfo>> datasets = new LinkedHashMap<>();

            for (Map<String, Object> row : rows)
            {
                String datasetName = (String)row.get("form_name");
                if (datasetName != null)
                {
                    if (!datasets.containsKey(datasetName))
                        datasets.put(datasetName, new ArrayList<ColumnInfo>());

                    createColumnInfo(datasets.get(datasetName), row);

                    // if we are matching the subject id by label, we need to track the actual generated name that
                    // redcap creates to uniquify the subject id per form
                    if (project.isMatchSubjectIdByLabel())
                    {
                        if (project.subjectIdMatches((String)row.get("field_label")))
                        {
                            String name = (String)row.get("field_name");
                            _subjectIdFieldMap.put(datasetName, name);
                        }
                    }
                }
                else
                    _job.error("Unable to parse dataset metadata, no field named : form_name");
            }
            return datasets;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void writeDatasetMetadata(VirtualFile vf, Map<String, Map<String, List<ColumnInfo>>> datasets)
    {
        try {

            DatasetsDocument manifestXml = DatasetsDocument.Factory.newInstance();
            DatasetsDocument.Datasets dsXml = manifestXml.addNewDatasets();
            DatasetsDocument.Datasets.Datasets2 datasets2Xml = dsXml.addNewDatasets();
            int datasetId = 1;

            // Create dataset metadata file
            TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
            TablesType tablesXml = tablesDoc.addNewTables();
            Map<String, RedcapConfiguration.RedcapProject> projectMap = _config.getProjectMap();

            for (Map.Entry<String, Map<String, List<ColumnInfo>>> entry : datasets.entrySet())
            {
                String projectName = entry.getKey();
                Map<String, List<ColumnInfo>> projDatasets = entry.getValue();
                RedcapConfiguration.RedcapProject project = projectMap.get(projectName);
                Map<String, RedcapConfiguration.RedcapForm> formMap = project.getFormMap();
                boolean isProjectDemographic = project.isDemographic();

                for (String datasetName : projDatasets.keySet())
                {
                    if (!isDatasetMerged(projectName, datasetName))
                    {
                        _datasetNameToId.put(datasetName, datasetId);

                        DatasetsDocument.Datasets.Datasets2.Dataset datasetXml = datasets2Xml.addNewDataset();
                        datasetXml.setName(datasetName);
                        datasetXml.setId(datasetId++);

                        datasetXml.setType("Standard");

                        if (formMap.containsKey(datasetName))
                        {
                            RedcapConfiguration.RedcapForm form = formMap.get(datasetName);
                            if (form.isDemographic())
                                datasetXml.setDemographicData(true);
                        }
                        else if (isProjectDemographic)
                            datasetXml.setDemographicData(true);

                        // create the dataset schemas
                        TableType tableXml = tablesXml.addNewTable();
                        tableXml.setTableName(datasetName);
                        tableXml.setTableDbType("TABLE");

                        TableType.Columns columnsXml = tableXml.addNewColumns();

                        for (ColumnInfo column : projDatasets.get(datasetName))
                        {
                            ColumnType columnXml = columnsXml.addNewColumn();
                            writeColumn(column, columnXml);
                        }
                    }
                }
            }
            String datasetFilename = vf.makeLegalName("RedCap Integration.dataset");

            try (PrintWriter writer = vf.getPrintWriter(datasetFilename))
            {
                writer.println("# default group can be used to avoid repeating definitions for each dataset\n" +
                        "#\n" +
                        "# action=[REPLACE,APPEND,DELETE] (default:REPLACE)\n" +
                        "# deleteAfterImport=[TRUE|FALSE] (default:FALSE)\n" +
                        "\n" +
                        "default.action=REPLACE\n" +
                        "default.deleteAfterImport=FALSE\n" +
                        "\n" +
                        "# map a source tsv column (right side) to a property name or full propertyURI (left)\n" +
                        "# predefined properties: ParticipantId, SiteId, VisitId, Created\n" +
                        "default.property.ParticipantId=ptid\n" +
                        "default.property.Created=dfcreate\n" +
                        "\n" +
                        "# use to map from filename->datasetid\n" +
                        "# NOTE: if there are NO explicit import definitions, we will try to import all files matching pattern\n" +
                        "# NOTE: if there are ANY explicit mapping, we will only import listed datasets\n" +
                        "\n" +
                        "default.filePattern=dataset(\\\\d*).tsv\n" +
                        "default.importAllMatches=TRUE");
            }

            dsXml.setMetaDataFile(SCHEMA_FILENAME);
            vf.saveXmlBean(MANIFEST_FILENAME, manifestXml);
            vf.saveXmlBean(SCHEMA_FILENAME, tablesDoc);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses the results of a project data export command. The redcap api will return a combined dataset for all forms
     * contained in the project.
     * @param loader
     * @return
     */
    private List<Map<String, Object>> parseDatasetData(DataLoader loader)
    {
        try {

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> row : loader.load())
            {
                Map<String, Object> newRow = new HashMap<>();
                Map<String, StringBuilder> multiValueFields = new HashMap<>();

                for (Map.Entry<String, Object> entry : row.entrySet())
                {
                    ColumnInfo col = _columnInfoMap.get(entry.getKey());
                    if (col != null)
                    {
                        if (col.getFk() != null && entry.getValue() != null)
                        {
                            Object lk = parseLookup(col, entry.getValue().toString());
                            if (lk != null)
                                newRow.put(entry.getKey(), lk);
                        }
                        else if (col.isBooleanType() && entry.getValue() != null)
                        {
                            Object lk = parseLookup(col, entry.getValue().toString());
                            if (lk != null)
                                newRow.put(entry.getKey(), lk);
                        }
                        else
                            newRow.put(entry.getKey(), entry.getValue());
                    }
                    else if (REDCAP_EVENT_COL_NAME.equalsIgnoreCase(entry.getKey()))
                    {
                        newRow.put(entry.getKey(), entry.getValue());
                    }
                    else if (entry.getKey().endsWith("_complete"))
                    {
                        newRow.put(entry.getKey(), entry.getValue());
                    }
                    else
                    {
                        ColumnInfo mvCol = findMultiValueColumn(entry.getKey());
                        if (mvCol != null)
                        {
                            String delim = ",";
                            String displayValue = parseDisplayValue(col, entry.getValue());
                            if (displayValue != null)
                            {
                                if (!multiValueFields.containsKey(mvCol.getName()))
                                {
                                    multiValueFields.put(mvCol.getName(), new StringBuilder());
                                    delim = "";
                                }
                                multiValueFields.get(mvCol.getName()).append(delim).append(displayValue);
                            }
                        }
                    }
                }

                for (Map.Entry<String, StringBuilder> mvEntry : multiValueFields.entrySet())
                    newRow.put(mvEntry.getKey(), mvEntry.getValue().toString());

                rows.add(newRow);
            }
            return rows;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private ColumnInfo findMultiValueColumn(String name)
    {
        if (!_multiValueColumns.isEmpty())
        {
            int idx = name.indexOf("___");
            if (idx != -1)
                return _multiValueColumns.get(name.substring(0, idx));
        }
        return null;
    }

    private Object parseLookup(ColumnInfo col, String value)
    {
        try {
            String[] parts = value.split(",");

            if (parts.length >= 1)
            {
                if (col.getJdbcType().isInteger())
                    return new Integer(parts[0].trim());
                else
                    return parts[0].trim();
            }
        }
        catch (Exception e)
        {
            _job.error(e.getMessage());
        }
        return null;
    }

    private String parseDisplayValue(ColumnInfo col, Object value)
    {
        try {
            if (value != null)
            {
                String[] parts = value.toString().split(",");

                if (parts.length == 2)
                    return parts[1].trim();
            }
        }
        catch (Exception e)
        {
            _job.error("Error occurred parsing column display values", e);
        }
        return null;
    }

    /**
     * Create the individual dataset tsv's for a project from the unified data export
     */
    private void createDatasetData(Map<String, List<Map<String, Object>>> datasetData, RedcapConfiguration.RedcapProject project, Map<String, List<ColumnInfo>> metadata, List<Map<String, Object>> data)
    {
        Map<String, RedcapConfiguration.RedcapForm> formMap = project.getFormMap();

        for (String datasetName : metadata.keySet())
        {
            List<Map<String, Object>> datasetRows;
            if (datasetData.containsKey(datasetName))
                datasetRows = datasetData.get(datasetName);
            else
            {
                datasetRows = new ArrayList<>();
                datasetData.put(datasetName, datasetRows);
            }

            List<ColumnInfo> columns = metadata.get(datasetName);
            String dateFieldName = null;

            if (formMap.containsKey(datasetName) && _config.getTimepointType().equals(TimepointType.DATE))
            {
                String dateField = formMap.get(datasetName).getDateField();
                if (dateField != null)
                    dateFieldName = dateField;
            }

            String isCompleteCol = datasetName + "_complete";
            String subjectIdField = getSubjectIdField(datasetName, project);

            for (Map<String, Object> row : data)
            {
                if (shouldExport(isCompleteCol, row))
                {
                    Map<String, Object> datasetRow = new HashMap<String, Object>();
                    String subjectId = (String)row.get(subjectIdField);

                    if (subjectId != null)
                    {
                        datasetRow.put("participantId", subjectId);

                        if (_config.getTimepointType().equals(TimepointType.VISIT))
                        {
                            String eventName = (String)row.get(REDCAP_EVENT_COL_NAME);
                            if (_visitToSequenceNumber.containsKey(eventName))
                            {
                                datasetRow.put("sequenceNum", _visitToSequenceNumber.get(eventName));
                            }
                            else if (_visitToSequenceNumber.containsKey("default_event_name"))
                            {
                                datasetRow.put("sequenceNum", _visitToSequenceNumber.get("default_event_name"));
                            }
                            else
                            {
                                _job.error("Unknown event name : " + eventName);
                                continue;
                            }
                        }
                        else
                        {
                            if (dateFieldName != null && row.containsKey(dateFieldName))
                                datasetRow.put("date", row.get(dateFieldName));
                            else
                                datasetRow.put("date", new Date());
                        }

                        for (ColumnInfo col : columns)
                        {
                            Object value = row.get(col.getName());

                            if (col.getJdbcType().equals(JdbcType.TIMESTAMP) && value != null)
                            {
                                ConvertHelper.LenientTimeOnlyConverter converter = new ConvertHelper.LenientTimeOnlyConverter();

                                Date timestamp = (Date)converter.convert(Date.class, value);
                                datasetRow.put(col.getName(), timestamp);
                            }
                            else
                                datasetRow.put(col.getName(), value);
                        }
                        datasetRows.add(datasetRow);
                    }
                    else
                        _job.warn("Unable to locate subject id field: " + subjectIdField + " for dataset: " + datasetName + " row ignored.");
                }
            }
        }
    }

    /**
     * Creates the individual dataset exports
     */
    private void writeDatasetData(Map<String, List<Map<String, Object>>> datasetData, VirtualFile vf)
    {
        try {
            int datasetId;

            for (Map.Entry<String, List<Map<String, Object>>> entry : datasetData.entrySet())
            {
                datasetId = _datasetNameToId.get(entry.getKey());

                String fileName = String.format("dataset%03d.tsv", datasetId);

                TSVMapWriter tsvWriter = new TSVMapWriter(entry.getValue());
                PrintWriter out = vf.getPrintWriter(fileName);
                tsvWriter.write(out);
                tsvWriter.close();
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private String getSubjectIdField(String datasetName, RedcapConfiguration.RedcapProject project)
    {
        String subjectIdField = null;

        if (project.isMatchSubjectIdByLabel())
            subjectIdField = _subjectIdFieldMap.get(datasetName);
        else
            subjectIdField = project.getSubjectId();

        return subjectIdField;
    }

    private boolean shouldExport(String isCompleteCol, Map<String, Object> row)
    {
        String isCompleteValue = (String)row.get(isCompleteCol);

        if (isCompleteValue != null)
        {
            if (isCompleteValue.startsWith("0"))
                return false;
        }
        return true;
    }

    private void writeColumn(ColumnInfo col, ColumnType columnXml)
    {
        columnXml.setColumnName(col.getName());

        Class clazz = col.getJavaClass();
        Type t = Type.getTypeByClass(clazz);

        if (null == t)
        {
            _job.error(col.getName() + " has unknown java class " + clazz.getName());
            throw new IllegalStateException(col.getName() + " has unknown java class " + clazz.getName());
        }

        columnXml.setDatatype(t.getSqlTypeName());

        if (null != col.getLabel())
            columnXml.setColumnTitle(col.getLabel());

        if (null != col.getDescription())
            columnXml.setDescription(col.getDescription());

/*
        if (!column.isNullable())
            columnXml.setNullable(false);
*/

        String formatString = col.getFormat();
        if (null != formatString)
            columnXml.setFormatString(formatString);

        ForeignKey fk = col.getFk();

        if (null != fk && null != fk.getLookupColumnName())
        {
            if (_lookups.containsKey(col.getName()))
            {
                // Make sure public Name and SchemaName aren't null before adding the FK
                String tinfoPublicName = col.getName();
                String tinfoPublicSchemaName = "lists";
                if (null != tinfoPublicName && null != tinfoPublicSchemaName)
                {
                    ColumnType.Fk fkXml = columnXml.addNewFk();

                    fkXml.setFkDbSchema(tinfoPublicSchemaName);
                    fkXml.setFkTable(tinfoPublicName);
                    fkXml.setFkColumnName(fk.getLookupColumnName());
                }
            }
        }
    }

    public void createColumnInfo(List<ColumnInfo> dataset, Map<String, Object> row)
    {
        String name = (String)row.get("field_name");
        String label = (String)row.get("field_label");
        String note = (String)row.get("field_note");
        String type = (String)row.get("field_type");
        String validation = (String)row.get("text_validation_type_or_show_slider_number");
        String choices = (String)row.get("select_choices_or_calculations");

        ColumnInfo col = new ColumnInfo(name);

        col.setLabel(label);
        col.setDescription(note);

        setType(col, type, validation, choices);
        dataset.add(col);

        _columnInfoMap.put(col.getName(), col);
    }

    private void setType(ColumnInfo col, String fieldType, String validationType, String choices)
    {
        if (fieldType != null)
        {
            if ("text".equalsIgnoreCase(fieldType))
            {
                if ("integer".equalsIgnoreCase(validationType))
                    col.setJdbcType(JdbcType.INTEGER);
                else if ("number".equalsIgnoreCase(validationType))
                    col.setJdbcType(JdbcType.DOUBLE);
                else if ("time".equalsIgnoreCase(validationType))
                {
                    col.setJdbcType(JdbcType.TIMESTAMP);
                    col.setFormat("K:mm a");
                }
                else if ("date_dmy".equalsIgnoreCase(validationType))
                    col.setJdbcType(JdbcType.DATE);
                else if ("date_ymd".equalsIgnoreCase(validationType))
                    col.setJdbcType(JdbcType.DATE);
                else if ("date_mdy".equalsIgnoreCase(validationType))
                    col.setJdbcType(JdbcType.DATE);
                else if (validationType != null && validationType.startsWith("datetime"))
                    col.setJdbcType(JdbcType.DATE);
                else
                    col.setJdbcType(JdbcType.VARCHAR);
            }
            else if ("notes".equalsIgnoreCase(fieldType))
            {
                col.setJdbcType(JdbcType.VARCHAR);
                col.setInputType("textarea");
            }
            else if ("truefalse".equalsIgnoreCase(fieldType) || "yesno".equalsIgnoreCase(fieldType))
            {
                col.setJdbcType(JdbcType.BOOLEAN);
            }
            else if ("dropdown".equalsIgnoreCase(fieldType) || "radio".equalsIgnoreCase(fieldType))
            {
                col.setJdbcType(JdbcType.INTEGER);

                // need to create the fk at export time
                if (choices != null)
                {
                    ForeignKey fk = new LookupForeignKey("key")
                    {
                        @Override
                        public TableInfo getLookupTableInfo()
                        {
                            return null;
                        }
                    };
                    col.setFk(fk);
                    createLookup(col, choices);
                }
            }
            else if ("checkbox".equalsIgnoreCase(fieldType))
            {
                col.setJdbcType(JdbcType.VARCHAR);
                _multiValueColumns.put(col.getName(), col);
            }
            else if ("file".equalsIgnoreCase(fieldType))
            {
            }
        }
        else
        {
            _job.error("Field type cannot be null");
            throw new IllegalArgumentException("Field type cannot be null");
        }
    }

    private void createLookup(ColumnInfo col, String choicesStr)
    {
        if (!_lookups.containsKey(col.getName()))
        {
            List<Map<String, Object>> lookup = new ArrayList<>();
            boolean integerKeyField = isIntegerKey(choicesStr);

            if (!integerKeyField)
                col.setJdbcType(JdbcType.VARCHAR);

            for (String choice : choicesStr.split("\\|"))
            {
                String[] lu = choice.split(",");
                if (lu.length == 2)
                {
                    Map<String, Object> row = new HashMap<>();
                    String key = lu[0].trim();

                    try {
                        if (integerKeyField)
                            row.put(LOOKUP_KEY_FIELD, new Integer(key));
                        else
                            row.put(LOOKUP_KEY_FIELD, key);
                        row.put(LOOKUP_NAME_FIELD, lu[1]);

                        lookup.add(row);
                    }
                    catch (Exception e)
                    {
                        _job.error(e.getMessage());
                    }
                }
            }
            _lookups.put(col.getName(), lookup);
        }
    }

    /**
     * Look at all the key values in the choices string to see if they are all integer
     * values.
     * @param choicesStr
     * @return
     */
    private boolean isIntegerKey(String choicesStr)
    {
        for (String choice : choicesStr.split("\\|"))
        {
            String[] lu = choice.split(",");
            if (lu.length == 2)
            {
                try {
                    Integer.parseInt(lu[0].trim());
                }
                catch (NumberFormatException e)
                {
                    return false;
                }
            }
        }
        return true;
    }

    private void writeStudy(VirtualFile vf)
    {
        try {
            StudyDocument doc = StudyDocument.Factory.newInstance();
            StudyDocument.Study studyXml = doc.addNewStudy();

            // Archive version
            studyXml.setArchiveVersion(13.11);

            // Study attributes
            studyXml.setLabel("RedCap Integration");

            if (_config.getTimepointType().equals(TimepointType.VISIT))
            {
                studyXml.setTimepointType(_config.getTimepointType());
                StudyDocument.Study.Visits visitsXml = studyXml.addNewVisits();
                visitsXml.setFile(VISIT_FILENAME);
            }
            else
                studyXml.setTimepointType(TimepointType.DATE);
            studyXml.setSecurityType(SecurityType.BASIC_READ);

            StudyDocument.Study.Datasets datasetsXml = studyXml.addNewDatasets();
            datasetsXml.setDir(DEFAULT_DIRECTORY);
            datasetsXml.setFile(MANIFEST_FILENAME);

            if (!_lookups.isEmpty())
            {
                ExportDirType listsDir = studyXml.addNewLists();
                listsDir.setDir(LISTS_DIRECTORY);
            }
            StudyDocument.Study.Datasets.Definition definitionXml = datasetsXml.addNewDefinition();
            String datasetFilename = vf.makeLegalName("RedCap Integration.dataset");
            definitionXml.setFile(datasetFilename);

            // Save the study.xml file.  This gets called last, after all other writers have populated the other sections.
            vf.saveXmlBean("study.xml", doc);
        }
        catch (IOException e)
        {
            _job.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Loop through all the configured projects to export REDCap events so they can be translated to study visits
     */
    private List<Map<String, Object>> getVisits(HttpClient client) throws Exception
    {
        List<Map<String, Object>> events = new ArrayList<>();

        for (RedcapConfiguration.RedcapProject project : _config.getProjects())
        {
            String token = _config.getToken(project.getProjectName());
            if (token != null)
            {
                ExportEventsCommand exportEventsCmd = new ExportEventsCommand(project.getServerUrl(), token);
                RedcapCommandResponse response = exportEventsCmd.execute(client);

                if (response != null && response.getStatusCode() == HttpStatus.SC_OK)
                {
                    events.addAll(response.getLoader().load());
                }
                else
                {
                    // if we are trying to export a visit based study, but there are not events for every
                    // row of data exported, we need to create a default timepoint
                    _createDefaultTimepoint = true;
                    _job.warn("No REDCap events to export, status code : " + response.getStatusCode());
                }
            }
            else
                _job.error("Could not find _netrc entry for host : " + project.getServerUrl() + " and project : " + project.getProjectName());
        }
        return events;
    }

    private void writeVisits(VirtualFile vf, List<Map<String, Object>> events)
    {
        try {

            VisitMapDocument visitMapDoc = VisitMapDocument.Factory.newInstance();
            VisitMapDocument.VisitMap visitMapXml = visitMapDoc.addNewVisitMap();
            int baseOffset = 0;

            if (_createDefaultTimepoint)
            {
                // add a single default visit for the case where there is are no redcap events to export
                VisitMapDocument.VisitMap.Visit visitXml = visitMapXml.addNewVisit();

                visitXml.setLabel("Timepoint 1");
                visitXml.setSequenceNum(0);
                visitXml.setMaxSequenceNum(99);

                _visitToSequenceNumber.put("default_event_name", 0d);
                baseOffset = 1;
            }

            if (!events.isEmpty())
            {
                for (Map<String, Object> event : events)
                {
                    String label = (String)event.get("unique_event_name");
                    String offset = (String)event.get("day_offset");
                    int dayOffset = offset != null ? Integer.parseInt(offset) : 0;
                    dayOffset += baseOffset;

                    if (label == null)
                        label = (String)event.get("event_name");

                    if (label != null)
                    {
                        VisitMapDocument.VisitMap.Visit visitXml = visitMapXml.addNewVisit();
                        double sequenceNum = 100 * dayOffset;

                        visitXml.setLabel(label);
                        visitXml.setSequenceNum(sequenceNum);
                        visitXml.setMaxSequenceNum(sequenceNum + 99);

                        if (!_visitToSequenceNumber.containsKey(label))
                            _visitToSequenceNumber.put(label, sequenceNum);
                    }
                }
            }

            vf.saveXmlBean(VISIT_FILENAME, visitMapDoc);
        }
        catch (IOException e)
        {
            _job.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void writeLookups(VirtualFile vf)
    {
        try {
            TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
            TablesType tablesXml = tablesDoc.addNewTables();

            for (Map.Entry<String, List<Map<String, Object>>> entry : _lookups.entrySet())
            {
                // Write meta data
                // create the dataset schemas
                TableType tableXml = tablesXml.addNewTable();
                tableXml.setTableName(entry.getKey());
                tableXml.setTableDbType("TABLE");

                TableType.Columns columnsXml = tableXml.addNewColumns();

                tableXml.setPkColumnName(LOOKUP_KEY_FIELD);
                for (ColumnInfo column : getLookupColumns(entry.getKey()))
                {
                    ColumnType columnXml = columnsXml.addNewColumn();
                    writeColumn(column, columnXml);
                }

                TSVMapWriter tsvWriter = new TSVMapWriter(entry.getValue());
                PrintWriter out = vf.getPrintWriter(entry.getKey() + ".tsv");
                tsvWriter.write(out);
                tsvWriter.close();
            }
            vf.saveXmlBean("lists.xml", tablesDoc);
        }
        catch (IOException e)
        {
            _job.error("An error occurred writing out the lookups", e);
            throw new RuntimeException(e);
        }
    }

    private List<ColumnInfo> getLookupColumns(String columnName)
    {
        List<ColumnInfo> columns = new ArrayList<>();

        ColumnInfo parentCol = _columnInfoMap.get(columnName);
        if (parentCol != null)
        {
            ColumnInfo col = new ColumnInfo(LOOKUP_KEY_FIELD);

            col.setJdbcType(parentCol.getJdbcType());
            col.setKeyField(true);
            columns.add(col);

            ColumnInfo colName = new ColumnInfo(LOOKUP_NAME_FIELD);

            colName.setJdbcType(JdbcType.VARCHAR);
            columns.add(colName);
        }
        else
            _job.warn("Unable to locate the referencing column : " + columnName + " for the lookup list");
        return columns;
    }

    public static class RedcapTestCase extends Assert
    {
        @Test
        public void testVisitExport() throws Exception
        {
            RedcapConfiguration config = new RedcapConfiguration("redcap.test.net", "foo", "patient_id", new File("test.zip"), TimepointType.VISIT);
            RedcapConfiguration.RedcapProject project = config.getProjects().get(0);
            RedcapExport export = new RedcapExport(null);

            VirtualFile root = new MemoryVirtualFile();
            VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);

            Map<String, List<ColumnInfo>> projMetadata = export.parsetMetadata(project, getMetadata().getLoader());
            assertTrue(projMetadata.size() == 2);
            Map<String, Map<String, List<ColumnInfo>>> metadata = new HashMap<>();
            metadata.put("foo", projMetadata);

            List<Map<String, Object>> data = export.parseDatasetData(getDatasetData().getLoader());

            export.writeStudy(root);
            export._createDefaultTimepoint = true;
            export.writeVisits(vf, Collections.EMPTY_LIST);
            export.writeDatasetMetadata(vf, metadata);
            Map<String, List<Map<String, Object>>> datasetData = new HashMap<>();
            export.createDatasetData(datasetData, project, projMetadata, data);
            export.writeDatasetData(datasetData, vf);

            XmlObject bean = root.getXmlBean("study.xml");
            assertTrue(bean instanceof StudyDocument);

            StudyDocument studyDoc = (StudyDocument)bean;
            StudyDocument.Study study = studyDoc.getStudy();

            assertEquals(study.getTimepointType(), TimepointType.VISIT);

            XmlObject metadataDoc = vf.getXmlBean("datasets_metadata.xml");
            assertTrue(metadataDoc instanceof TablesDocument);
            XmlObject manifestDoc = vf.getXmlBean("datasets_manifest.xml");
            assertTrue(manifestDoc instanceof DatasetsDocument);

            byte[] b = new byte[100];

            InputStream ds_1 = vf.getInputStream("dataset001.tsv");
            assertTrue(ds_1.read(b) != -1);
            InputStream ds_2 = vf.getInputStream("dataset002.tsv");
            assertTrue(ds_2.read(b) != -1);
        }

        @Test
        public void testDateExport() throws Exception
        {
            RedcapConfiguration config = new RedcapConfiguration("redcap.test.net", "foo", "patient_id", new File("test.zip"), TimepointType.DATE);
            RedcapConfiguration.RedcapProject project = config.getProjects().get(0);
            RedcapExport export = new RedcapExport(null);

            VirtualFile root = new MemoryVirtualFile();
            VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);

            Map<String, List<ColumnInfo>> projMetadata = export.parsetMetadata(project, getMetadata().getLoader());
            assertTrue(projMetadata.size() == 2);
            Map<String, Map<String, List<ColumnInfo>>> metadata = new HashMap<>();
            metadata.put("foo", projMetadata);

            List<Map<String, Object>> data = export.parseDatasetData(getDatasetData().getLoader());

            export.writeStudy(root);
            export.writeDatasetMetadata(vf, metadata);
            Map<String, List<Map<String, Object>>> datasetData = new HashMap<>();
            export.createDatasetData(datasetData, project, projMetadata, data);
            export.writeDatasetData(datasetData, vf);

            XmlObject bean = root.getXmlBean("study.xml");
            assertTrue(bean instanceof StudyDocument);

            StudyDocument studyDoc = (StudyDocument)bean;
            StudyDocument.Study study = studyDoc.getStudy();

            assertEquals(study.getTimepointType(), TimepointType.DATE);

            XmlObject metadataDoc = vf.getXmlBean("datasets_metadata.xml");
            assertTrue(metadataDoc instanceof TablesDocument);
            XmlObject manifestDoc = vf.getXmlBean("datasets_manifest.xml");
            assertTrue(manifestDoc instanceof DatasetsDocument);

            byte[] b = new byte[100];

            InputStream ds_1 = vf.getInputStream("dataset001.tsv");
            assertTrue(ds_1.read(b) != -1);
            InputStream ds_2 = vf.getInputStream("dataset002.tsv");
            assertTrue(ds_2.read(b) != -1);
        }
    }

    private static RedcapCommandResponse getMetadata()
    {
        String responseText =
                "field_name, form_name, field_type, field_label, text_validation_type_or_show_slider_number\n" +
                        "patient_id, demographics, text, Patient ID, number\n" +
                        "patient_sex, demographics, text, Patient sex, \n" +
                        "patient_age, demographics, text, Patient age, number\n" +
                        "patient_notes, demographics, notes, Patient notes, number\n" +
                        "patient_id, lab results, text, Patient ID, number\n" +
                        "patient_sex_lr, lab results, text, Patient sex, \n" +
                        "patient_age_lr, lab results, text, Patient age, number\n" +
                        "patient_notes_lr, lab results, notes, Patient notes, number\n";

        return new RedcapCommandResponse(responseText, HttpStatus.SC_OK);
    }

    private static RedcapCommandResponse getDatasetData()
    {
        String responseText =
                "patient_id, patient_sex, patient_age, patient_notes, patient_sex_lr, patient_age_lr, patient_notes_lr\n" +
                        "2222, male, 33, blah, m, 33, blah\n" +
                        "22322, male, 43, blah, m, 33, blah\n" +
                        "22422, female, 63, blah, f, 33, blah\n" +
                        "22522, female, 23, blah, f, 33, blah\n" +
                        "22622, male, 37, blah, m, 33, blah\n";

        return new RedcapCommandResponse(responseText, HttpStatus.SC_OK);
    }
}
