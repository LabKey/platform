package org.labkey.freezerpro.export;

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
import org.labkey.freezerpro.FreezerProConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/21/2014.
 */
public class FreezerProExport
{
    private FreezerProConfig _config;
    private PipelineJob _job;
    private File _archive;

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
        List<Map<String, Object>> data = getSampleData(client);

        if (!data.isEmpty())
        {
            _job.info("requesting any user defined fields and location information");
            for(Map<String, Object> row : data)
            {
                if (row.containsKey("uid"))
                {
                    String id = String.valueOf(row.get("uid"));

                    // get any user defined fields
                    if (getConfig().isImportUserFields())
                        getSampleUserData(client, id, row);

                    // retrieve the sample location info
                    getSampleLocationData(client, id, row);
                }
            }
        }

        // write out the archive
        try {
            _job.info("data processing complete, a total of " + data.size() + " records were exported.");
            _job.info("creating the exported data .csv file");
            TSVMapWriter tsvWriter = new TSVMapWriter(data);
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

        try {
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
                ExportSamplesResponse samplesResponse = new ExportSamplesResponse(handler.handleResponse(response), status.getStatusCode(), null);
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
        ExportSamplesCommand exportSamplesCmd = new ExportSamplesCommand(_config.getBaseServerUrl(), _config.getUsername(), _config.getPassword());
        FreezerProCommandResonse response = exportSamplesCmd.execute(client, _job);

        try
        {
            if (response != null)
            {
                if (response.getStatusCode() == HttpStatus.SC_OK)
                {
                    _job.info("sample request successfull, parsing returned data.");
                    data = response.loadData();
                }
                else
                {
                    _job.error("request for sample data failed, status code: " + response.getStatusCode());
                    throw new RuntimeException("request for sample data failed, status code: " + response.getStatusCode());
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
        ExportSampleUserFieldsCommand exportUserFieldsCmd = new ExportSampleUserFieldsCommand(_config.getBaseServerUrl(), _config.getUsername(), _config.getPassword(), sampleId);
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

    private void getSampleLocationData(HttpClient client, String sampleId, Map<String, Object> row)
    {
        ExportLocationCommand exportLocationCommand = new ExportLocationCommand(_config.getBaseServerUrl(), _config.getUsername(), _config.getPassword(), sampleId);
        FreezerProCommandResonse response = exportLocationCommand.execute(client, _job);

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
                    _job.error("request for sample location data failed, status code: " + response.getStatusCode());
                    throw new RuntimeException("request for sample location data failed, status code: " + response.getStatusCode());
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

    static String translateFieldName(String fieldName)
    {
        if ("type".equalsIgnoreCase(fieldName))
            return "sample type";
        if ("barcode_tag".equalsIgnoreCase(fieldName))
            return "barcode";
        if ("sample_id".equalsIgnoreCase(fieldName))
            return "uid";
        if ("scount".equalsIgnoreCase(fieldName))
            return "vials";
        if ("created at".equalsIgnoreCase(fieldName))
            return "created";
        if ("box_name".equalsIgnoreCase(fieldName))
            return "box";

        // these should be configurable since they are user defined
        if ("time of draw".equalsIgnoreCase(fieldName))
            return "time of draw";
        if ("date of process".equalsIgnoreCase(fieldName))
            return "date of draw";

        return fieldName;
    }
}
