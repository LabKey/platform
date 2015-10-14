/*
 * Copyright (c) 2014-2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
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
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.Pair;
import org.labkey.freezerpro.FreezerProConfig;

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

    private Set<String> _columnSet = new HashSet<>();
    private static int RECORD_CHUNK_SIZE = 2000;

    public FreezerProExport(FreezerProConfig config, PipelineJob job, File archive)
    {
        _config = config;
        _job = job;
        _archive = archive;
    }

    public FreezerProConfig getConfig()
    {
        return _config;
    }

    public File exportRepository()
    {
        _job.info("Starting FreezerPro export");
        HttpClient client = HttpClients.createDefault();

        List<Map<String, Object>> data = new ArrayList<>();

        if (getConfig().isUseAdvancedSearch())
        {
            data = getAdvancedSampleData(client);
        }
        else
        {
            // FreezerPro search_samples is slow and should be avoided as a way to export samples. The preferred way is to
            // iterate over all freezers and request samples per each freezer
            if (getConfig().getSearchFilterString() != null)
            {
                data = getSampleData(client);
                // get location information
                Map<String, Map<String, Object>> locationMap = new HashMap<>();
                getSampleLocationData(client, locationMap);

                // merge the location information into the sample data
                for (Map<String, Object> dataRow : data)
                {
                    String sampleId = String.valueOf(FreezerProConfig.SAMPLE_ID_FIELD_NAME);

                    if (locationMap.containsKey(sampleId))
                    {
                        dataRow.putAll(locationMap.get(sampleId));
                    }
                }
            }
            else
            {
                getVialSamples(client, data);
            }

            // if there are any column filters in the configuration, perform the filtering
            data = filterColumns(data);

            if (getConfig().isGetUserFields() && !data.isEmpty())
            {
                _job.info("requesting any user defined fields and location information");
                int count = 0;
                for (Map<String, Object> row : data)
                {
                    if (_job.checkInterrupted())
                        return null;

                    if (row.containsKey(FreezerProConfig.SAMPLE_ID_FIELD_NAME))
                    {
                        String id = String.valueOf(row.get(FreezerProConfig.SAMPLE_ID_FIELD_NAME));

                        // get any user defined fields
                        getSampleUserData(client, id, row);
                        if ((++count % 1000) == 0)
                        {
                            _job.info("User defined fields = retrieved " + count + " records out of a total of " + data.size());
                        }
                    }
                }
            }

            // filter again to catch any column that may be user defined
            data = filterColumns(data);
        }

        // write out the archive
        try
        {
            _job.info("data processing complete, a total of " + data.size() + " records were exported.");
            _job.info("creating the exported data .csv file");
            try (TSVMapWriter tsvWriter = new TSVMapWriter(_columnSet, data))
            {
                tsvWriter.setDelimiterCharacter(TSVWriter.DELIM.COMMA);
                tsvWriter.write(_archive);
            }
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

    private List<Map<String, Object>> getAdvancedSampleData(HttpClient client)
    {
        List<Map<String, Object>> data = new ArrayList<>();
        _job.info("requesting sample data from server url: " + getConfig().getBaseServerUrl());

        try
        {
            int start = 0;
            int limit = RECORD_CHUNK_SIZE;
            boolean done = false;

            while (!done)
            {
                ExportAdvancedSamplesCommand exportAdvSamplesCmd = new ExportAdvancedSamplesCommand(this, getConfig(), false, start, limit);
                FreezerProCommandResponse response = exportAdvSamplesCmd.execute(client, _job);
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

            if (getConfig().isGetArchivedSamples())
            {
                done = false;
                start = 0;
                while (!done)
                {
                    ExportAdvancedSamplesCommand exportAdvSamplesCmd = new ExportAdvancedSamplesCommand(this, getConfig(), true, start, limit);
                    FreezerProCommandResponse response = exportAdvSamplesCmd.execute(client, _job);
                    if (response != null)
                    {
                        if (response.getStatusCode() == HttpStatus.SC_OK)
                        {
                            _job.info("sample request successful, parsing returned data.");
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

            if (getConfig().isGetSampleLocation())
            {
                // get location information
                Map<String, Map<String, Object>> locationMap = new HashMap<>();
                getSampleLocationData(client, locationMap);

                // merge the location information into the sample data
                for (Map<String, Object> dataRow : data)
                {
                    String sampleId = String.valueOf(dataRow.get(FreezerProConfig.SAMPLE_ID_FIELD_NAME));

                    if (locationMap.containsKey(sampleId))
                    {
                        dataRow.putAll(locationMap.get(sampleId));
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

    private List<Map<String, Object>> getSampleData(HttpClient client)
    {
        List<Map<String, Object>> data = new ArrayList<>();
        _job.info("requesting sample data from server url: " + getConfig().getBaseServerUrl());

        try
        {
            int start = 0;
            int limit = RECORD_CHUNK_SIZE;
            boolean done = false;

            while (!done)
            {
                ExportSamplesCommand exportSamplesCmd = new ExportSamplesCommand(this, getConfig().getBaseServerUrl(), getConfig().getUsername(),
                        getConfig().getPassword(), getConfig().getSearchFilterString(), start, limit);
                FreezerProCommandResponse response = exportSamplesCmd.execute(client, _job);
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
        ExportSampleUserFieldsCommand exportUserFieldsCmd = new ExportSampleUserFieldsCommand(this, getConfig().getBaseServerUrl(), getConfig().getUsername(), getConfig().getPassword(), sampleId);
        FreezerProCommandResponse response = exportUserFieldsCmd.execute(client, _job);

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
                ExportLocationCommand exportLocationCommand = new ExportLocationCommand(this, getConfig().getBaseServerUrl(),
                        getConfig().getUsername(), getConfig().getPassword(), start, limit);
                FreezerProCommandResponse response = exportLocationCommand.execute(client, _job);

                if (response != null)
                {
                    if (response.getStatusCode() == HttpStatus.SC_OK)
                    {
                        for (Map<String, Object> row : response.loadData())
                        {
                            String sampleId = String.valueOf(row.get(FreezerProConfig.SAMPLE_ID_FIELD_NAME));
                            String location = String.valueOf(row.get("location"));

                            if (!locationMap.containsKey(sampleId))
                            {
                                parseLocation(location, row);
                                locationMap.put(sampleId, row);
                            }
                        }
                        start += limit;
                        done = (response.getTotalRecords() == 0) || (start >= response.getTotalRecords());
                        _job.info("Location information = retrieved " + start + " records out of a total of " + response.getTotalRecords());
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

    private void parseLocation(String location, Map<String, Object> row)
    {
        // split on right arrow display string
        String[] parts = location.split("&rarr;");

        if (parts != null && parts.length >= 2)
        {
            if (!row.containsKey("freezer"))
                row.put(translateFieldName("freezer"), parts[0]);

            if (parts.length > 2 && !row.containsKey("level1"))
                row.put(translateFieldName("level1"), parts[1]);

            if (parts.length > 3 && !row.containsKey("level2"))
                row.put(translateFieldName("level2"), parts[2]);

            if (!row.containsKey("box"))
                row.put(translateFieldName("box"), parts[parts.length-1]);
        }
    }

    private List<Freezer> getFreezers(HttpClient client)
    {
        GetFreezersCommand sampleTypesCommand = new GetFreezersCommand(this, getConfig().getBaseServerUrl(), getConfig().getUsername(), getConfig().getPassword());
        FreezerProCommandResponse response = sampleTypesCommand.execute(client, _job);
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
                                        String.valueOf(row.get(FreezerProConfig.BARCODE_FIELD_NAME)),
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
            GetFreezerSamplesCommand sampleTypesCommand = new GetFreezerSamplesCommand(this, getConfig().getBaseServerUrl(), getConfig().getUsername(), getConfig().getPassword(), freezer.getId());
            FreezerProCommandResponse response = sampleTypesCommand.execute(client, _job);

            try
            {
                if (response != null)
                {
                    if (response.getStatusCode() == HttpStatus.SC_OK)
                    {
                        List<Map<String, Object>> data = response.loadData();
                        _job.info("Loading " + data.size() + " records from freezer : " + freezer.getName());
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

    private void getVialSamples(HttpClient client, List<Map<String, Object>> rows)
    {
        try
        {
            int start = 0;
            int limit = RECORD_CHUNK_SIZE;
            boolean done = false;

            while (!done)
            {
                GetVialSamplesCommand vialSamplesCommand = new GetVialSamplesCommand(this, getConfig().getBaseServerUrl(),
                        getConfig().getUsername(), getConfig().getPassword(), start, limit);
                FreezerProCommandResponse response = vialSamplesCommand.execute(client, _job);
                if (response != null)
                {
                    if (response.getStatusCode() == HttpStatus.SC_OK)
                    {
                        for (Map<String, Object> row : response.loadData())
                        {
                            String location = String.valueOf(row.get("location"));
                            if (location != null)
                                parseLocation(location, row);

                            rows.add(row);
                        }
                        start += limit;
                        done = (response.getTotalRecords() == 0) || (start >= response.getTotalRecords());
                        _job.info("Vial information = retrieved " + start + " records out of a total of " + response.getTotalRecords());
                    }
                    else
                    {
                        _job.error("request for vial data failed, status code: " + response.getStatusCode());
                        throw new RuntimeException("request for vial data failed, status code: " + response.getStatusCode());
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    static boolean exportField(String fieldName)
    {
        return true;
    }

    public String translateFieldName(String fieldName)
    {
        if (getConfig().getColumnMap().containsKey(fieldName))
            fieldName = getConfig().getColumnMap().get(fieldName);

        // keep track of the global column list for the export
        if (!_columnSet.contains(fieldName))
            _columnSet.add(fieldName);

        return fieldName;
    }

    private List<Map<String, Object>> filterColumns(List<Map<String, Object>> data)
    {
        if (!getConfig().getColumnFilters().isEmpty())
        {
            List<Map<String, Object>> newData = new ArrayList<>();

            for (Map<String, Object> record : data)
            {
                boolean addRecord = true;
                for (Pair<String, List<String>> filter : getConfig().getColumnFilters())
                {
                    if (record.containsKey(filter.getKey()) && !valueAllowed(record.get(filter.getKey()), filter.getValue()))
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

    private boolean valueAllowed(Object value, List<String> allowed)
    {
        String str = String.valueOf(value);
        for (String match : allowed)
        {
            if (match.equals(str))
                return true;
        }
        return false;
    }

    public PipelineJob getJob()
    {
        return _job;
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
