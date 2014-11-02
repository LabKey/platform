/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.freezerpro.export;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.Pair;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.freezerpro.FreezerProConfig;
import org.labkey.study.xml.freezerProExport.FreezerProConfigDocument;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 5/21/2014.
 */
public class FreezerProExport
{
    private FreezerProConfig _config;
    private PipelineJob _job;
    private File _archive;

    private String _searchFilterString;
    private boolean _getUserFields;
    private static Map<String, String> COLUMN_MAP;
    private List<Pair<String, Object>> _columnFilters = new ArrayList<>();
    private Map<String, String> _columnMap = new CaseInsensitiveHashMap<>();
    private Set<String> _columnSet = new HashSet<>();
    private static int RECORD_CHUNK_SIZE = 1000;

    public static final String BARCODE_FIELD_NAME = "barcode";
    public static final String SAMPLE_ID_FIELD_NAME = "uid";

    static
    {
        COLUMN_MAP = new CaseInsensitiveHashMap<>();

        COLUMN_MAP.put("type", "sample type");
        COLUMN_MAP.put("barcode_tag", BARCODE_FIELD_NAME);
        COLUMN_MAP.put("sample_id", SAMPLE_ID_FIELD_NAME);
        COLUMN_MAP.put("scount", "vials");
        COLUMN_MAP.put("created at", "created");
        COLUMN_MAP.put("box_name", "box");
        COLUMN_MAP.put("time of draw", "time of draw");
        COLUMN_MAP.put("date of process", "date of draw");
    }

    public FreezerProExport(FreezerProConfig config, PipelineJob job, File archive)
    {
        _config = config;
        _job = job;
        _archive = archive;

        _columnMap.putAll(COLUMN_MAP);
        if (config.getMetadata() != null)
            parseConfigMetadata(config.getMetadata());
    }

    public FreezerProConfig getConfig()
    {
        return _config;
    }

    private void parseConfigMetadata(String metadata)
    {
        if (metadata != null)
        {
            try
            {
                FreezerProConfigDocument doc = FreezerProConfigDocument.Factory.parse(metadata, XmlBeansUtil.getDefaultParseOptions());
                FreezerProConfigDocument.FreezerProConfig config = doc.getFreezerProConfig();

                if (config != null)
                {
                    // import user defined fields
                    _getUserFields = config.getGetUserFields();
                    if (config.isSetFilterString())
                        _searchFilterString = config.getFilterString();

                    FreezerProConfigDocument.FreezerProConfig.ColumnFilters columnFilters = config.getColumnFilters();
                    if (columnFilters != null)
                    {
                        for (FreezerProConfigDocument.FreezerProConfig.ColumnFilters.Filter filter : columnFilters.getFilterArray())
                        {
                            if (filter.getName() != null && filter.getValue() != null)
                                _columnFilters.add(new Pair<String, Object>(filter.getName(), filter.getValue()));
                        }
                    }

                    FreezerProConfigDocument.FreezerProConfig.ColumnMap columnMap = config.getColumnMap();
                    if (columnMap != null)
                    {
                        for (FreezerProConfigDocument.FreezerProConfig.ColumnMap.Column col : columnMap.getColumnArray())
                        {
                            if (col.getSourceName() != null && col.getDestName() != null)
                            {
                                if (!COLUMN_MAP.containsKey(col.getSourceName()))
                                    _columnMap.put(col.getSourceName(), col.getDestName());
                                else
                                    _job.warn("The column name: " + col.getSourceName() + " is reserved and cannot be remapped. " +
                                            "The configuration specificed to remap to: " + col.getDestName() + " will be ignored.");
                            }
                        }
                    }
                }
            }
            catch (XmlException e)
            {
                _job.error("The FreezerPro metadata XML was malformed. The error was returned: " + e.getMessage());
            }
        }
    }

    public File exportRepository()
    {
        _job.info("Starting FreezerPro export");
        HttpClient client = HttpClients.createDefault();

        List<Map<String, Object>> data = new ArrayList<>();

        // FreezerPro search_samples is slow and should be avoided as a way to export samples. The preferred way is to
        // iterate over all freezers and request samples per each freezer
        if (_searchFilterString != null)
        {
            data = getSampleData(client);
        }
        else
        {
            for (Freezer freezer : getFreezers(client))
            {
                if (_job.checkInterrupted())
                    return null;

                getSamplesForFreezer(client, freezer, data);
            }
        }

        // get location information
        Map<String, Map<String, Object>> locationMap = new HashMap<>();
        getSampleLocationData(client, locationMap);

        // merge the location information into the sample data
        for (Map<String, Object> dataRow : data)
        {
            String sampleId = String.valueOf(dataRow.get(SAMPLE_ID_FIELD_NAME));

            if (locationMap.containsKey(sampleId))
            {
                dataRow.putAll(locationMap.get(sampleId));
            }
        }

        // if there are any column filters in the configuration, perform the filtering
        data = filterColumns(data);

        if (_getUserFields && !data.isEmpty())
        {
            _job.info("requesting any user defined fields and location information");
            for (Map<String, Object> row : data)
            {
                if (_job.checkInterrupted())
                    return null;

                if (row.containsKey(SAMPLE_ID_FIELD_NAME))
                {
                    String id = String.valueOf(row.get(SAMPLE_ID_FIELD_NAME));

                    // get any user defined fields
                    getSampleUserData(client, id, row);

                    // retrieve the sample location info
/*
                    if (row.containsKey("loc_id"))
                    {
                        String locationId = String.valueOf(row.get("loc_id"));
                        getSampleLocationData(client, locationId, row);
                    }
*/
                }
            }
        }

        // filter again to catch any column that may be user defined
        data = filterColumns(data);

        // write out the archive
        try
        {
            _job.info("data processing complete, a total of " + data.size() + " records were exported.");
            _job.info("creating the exported data .csv file");
            TSVMapWriter tsvWriter = new TSVMapWriter(_columnSet, data);
            tsvWriter.setDelimiterCharacter(TSVWriter.DELIM.COMMA);
            tsvWriter.write(_archive);
            tsvWriter.close();
            _job.info("finished writing data file: " + _archive.getName());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return _archive;
    }

    /**
     * Tests the connection configuration
     * @return
     * @throws ValidationException
     */
    public void testConnection() throws ValidationException
    {
        HttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost(getConfig().getBaseServerUrl());

        try
        {
            List<NameValuePair> params = new ArrayList<NameValuePair>();

            params.add(new BasicNameValuePair("method", "search_samples"));
            params.add(new BasicNameValuePair("username", getConfig().getUsername()));
            params.add(new BasicNameValuePair("password", getConfig().getPassword()));
            params.add(new BasicNameValuePair("query", ""));
            params.add(new BasicNameValuePair("limit", "1"));

            post.setEntity(new UrlEncodedFormEntity(params));

            ResponseHandler<String> handler = new BasicResponseHandler();
            HttpResponse response = client.execute(post);
            StatusLine status = response.getStatusLine();

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
            {
                ExportSamplesResponse samplesResponse = new ExportSamplesResponse(this, handler.handleResponse(response), status.getStatusCode(), null);
                samplesResponse.loadData();
            }
            else
                throw new ValidationException("Attempted connection to the FreezerPro server failed with a status code of: " + response.getStatusLine().getStatusCode());
        }
        catch (Exception e)
        {
            throw new ValidationException(e.getMessage());
        }
    }

    private List<Map<String, Object>> getSampleData(HttpClient client)
    {
        List<Map<String, Object>> data = new ArrayList<>();
        _job.info("requesting sample data from server url: " + _config.getBaseServerUrl());

        try
        {
            int start = 0;
            int limit = RECORD_CHUNK_SIZE;
            boolean done = false;

            while (!done)
            {
                ExportSamplesCommand exportSamplesCmd = new ExportSamplesCommand(this, _config.getBaseServerUrl(), _config.getUsername(),
                        _config.getPassword(), _searchFilterString, start, limit);
                FreezerProCommandResonse response = exportSamplesCmd.execute(client, _job);
                if (response != null)
                {
                    if (response.getStatusCode() == HttpStatus.SC_OK)
                    {
                        _job.info("sample request successfull, parsing returned data.");
                        data.addAll(response.loadData());

                        start += limit;
                        done = (response.getTotalRecords() == 0) || (start >= response.getTotalRecords());
                    }
                    else
                    {
                        _job.error("request for sample data failed, status code: " + response.getStatusCode());
                        throw new RuntimeException("request for sample data failed, status code: " + response.getStatusCode());
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return data;
    }

    private void getSampleUserData(HttpClient client, String sampleId, Map<String, Object> row)
    {
        ExportSampleUserFieldsCommand exportUserFieldsCmd = new ExportSampleUserFieldsCommand(this, _config.getBaseServerUrl(), _config.getUsername(), _config.getPassword(), sampleId);
        FreezerProCommandResonse response = exportUserFieldsCmd.execute(client, _job);

        try
        {
            if (response != null)
            {
                if (response.getStatusCode() == HttpStatus.SC_OK)
                {
                    List<Map<String, Object>> data = response.loadData();
                    if (data.size() == 1)
                    {
                        for (Map.Entry<String, Object> entry : data.get(0).entrySet())
                        {
                            if (exportField(entry.getKey()))
                                row.put(translateFieldName(entry.getKey()), entry.getValue());
                        }
                    }
                }
                else
                {
                    _job.error("request for sample user data failed, status code: " + response.getStatusCode());
                    throw new RuntimeException("request for sample user data failed, status code: " + response.getStatusCode());
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void getSampleLocationData(HttpClient client, Map<String, Map<String, Object>> locationMap)
    {
        try
        {
            int start = 0;
            int limit = RECORD_CHUNK_SIZE;
            boolean done = false;

            while (!done)
            {
                ExportLocationCommand exportLocationCommand = new ExportLocationCommand(this, _config.getBaseServerUrl(),
                        _config.getUsername(), _config.getPassword(), start, limit);
                FreezerProCommandResonse response = exportLocationCommand.execute(client, _job);

                if (response != null)
                {
                    if (response.getStatusCode() == HttpStatus.SC_OK)
                    {
                        for (Map<String, Object> row : response.loadData())
                        {
                            String sampleId = String.valueOf(row.get(SAMPLE_ID_FIELD_NAME));

                            if (!locationMap.containsKey(sampleId))
                            {
                                locationMap.put(sampleId, row);
                            }
                        }
                        start += limit;
                        done = (response.getTotalRecords() == 0) || (start >= response.getTotalRecords());
                    }
                    else
                    {
                        _job.error("request for sample location data failed, status code: " + response.getStatusCode());
                        throw new RuntimeException("request for sample location data failed, status code: " + response.getStatusCode());
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private List<Freezer> getFreezers(HttpClient client)
    {
        GetFreezersCommand sampleTypesCommand = new GetFreezersCommand(this, _config.getBaseServerUrl(), _config.getUsername(), _config.getPassword());
        FreezerProCommandResonse response = sampleTypesCommand.execute(client, _job);
        List<Freezer> freezers = new ArrayList<>();

        try
        {
            if (response != null)
            {
                if (response.getStatusCode() == HttpStatus.SC_OK)
                {
                    for (Map<String, Object> row : response.loadData())
                    {
                        freezers.add(new Freezer(
                                        String.valueOf(row.get("id")),
                                        String.valueOf(row.get("name")),
                                        String.valueOf(row.get("description")),
                                        NumberUtils.toInt(String.valueOf(row.get("subdivisions"))),
                                        NumberUtils.toInt(String.valueOf(row.get("boxes"))),
                                        String.valueOf(row.get(BARCODE_FIELD_NAME)),
                                        String.valueOf(row.get("rfid_tag")))
                        );

                    }
                    return freezers;
                }
                else
                {
                    _job.error("request for freezer data failed, status code: " + response.getStatusCode());
                    throw new RuntimeException("request for freezer data failed, status code: " + response.getStatusCode());
                }
            }
            return Collections.EMPTY_LIST;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void getSamplesForFreezer(HttpClient client, Freezer freezer, List<Map<String, Object>> rows)
    {
        if (freezer.getId() != null)
        {
            GetFreezerSamplesCommand sampleTypesCommand = new GetFreezerSamplesCommand(this, _config.getBaseServerUrl(), _config.getUsername(), _config.getPassword(), freezer.getId());
            FreezerProCommandResonse response = sampleTypesCommand.execute(client, _job);

            try
            {
                if (response != null)
                {
                    if (response.getStatusCode() == HttpStatus.SC_OK)
                    {
                        List<Map<String, Object>> data = response.loadData();
                        for (Map<String, Object> row : data)
                        {
                            rows.add(row);
                        }
                    }
                    else
                    {
                        _job.error("request for sample types data failed, status code: " + response.getStatusCode());
                        throw new RuntimeException("request for sample types data failed, status code: " + response.getStatusCode());
                    }
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    static boolean exportField(String fieldName)
    {
        return true;
    }

    public String translateFieldName(String fieldName)
    {
        if (_columnMap.containsKey(fieldName))
            fieldName = _columnMap.get(fieldName);

        // keep track of the global column list for the export
        if (!_columnSet.contains(fieldName))
            _columnSet.add(fieldName);

        return fieldName;
    }

    private List<Map<String, Object>> filterColumns(List<Map<String, Object>> data)
    {
        if (!_columnFilters.isEmpty())
        {
            List<Map<String, Object>> newData = new ArrayList<>();

            for (Map<String, Object> record : data)
            {
                boolean addRecord = true;
                for (Pair<String, Object> filter : _columnFilters)
                {
                    if (record.containsKey(filter.getKey()) && !record.get(filter.getKey()).equals(filter.getValue()))
                    {
                        addRecord = false;
                        break;
                    }
                }
                if (addRecord)
                    newData.add(record);
            }
            return newData;
        }
        else
            return data;
    }

    public static class Freezer
    {
        private String _id;
        private String _name;
        private String _description;
        private int _subdivisions;
        private int _boxes;
        private String _barcodeTag;
        private String _rfidTag;

        public Freezer(String id, String name, String description, int subdivisions, int boxes, String barcodeTag, String rfidTag)
        {
            _id = id;
            _name = name;
            _description = description;
            _subdivisions = subdivisions;
            _boxes = boxes;
            _barcodeTag = barcodeTag;
            _rfidTag = rfidTag;
        }

        public String getId()
        {
            return _id;
        }

        public String getName()
        {
            return _name;
        }

        public String getDescription()
        {
            return _description;
        }

        public int getSubdivisions()
        {
            return _subdivisions;
        }

        public int getBoxes()
        {
            return _boxes;
        }

        public String getBarcodeTag()
        {
            return _barcodeTag;
        }

        public String getRfidTag()
        {
            return _rfidTag;
        }
    }
}