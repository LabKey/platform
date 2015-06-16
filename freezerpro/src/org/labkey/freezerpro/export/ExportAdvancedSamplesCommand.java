/*
 * Copyright (c) 2015 LabKey Corporation
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

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.freezerpro.FreezerProConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExportAdvancedSamplesCommand
{
    private FreezerProExport _export;
    private FreezerProConfig _config;
    private String _url;
    private String _username;
    private String _password;
    private boolean _exportArchivedSamples;
    private int _startRecord;
    private int _limit;

    public ExportAdvancedSamplesCommand(FreezerProExport export, FreezerProConfig config, boolean exportArchivedSamples, int startRecord, int limit)
    {
        _export = export;
        _config = config;
        _url = _config.getBaseServerUrl();
        _username = _config.getUsername();
        _password = _config.getPassword();
        _exportArchivedSamples = exportArchivedSamples;
        _startRecord = startRecord;
        _limit = limit;
    }

    public FreezerProCommandResponse execute(HttpClient client, PipelineJob job)
    {
        HttpPost post = new HttpPost(_url);

        try {
            JSONObject params = new JSONObject();
            List<Map<String, Object>> queryFilters = _config.getQueryFilters();

            if (_exportArchivedSamples)
            {
                Map<String, Object> archiveFilter = new HashMap<>();
                archiveFilter.put("field", "locations_count");
                archiveFilter.put("op", "eq");
                archiveFilter.put("value", 0.0);

                queryFilters.add(archiveFilter);
            }

            params.put("method", "advanced_search");
            params.put("username", _username);
            params.put("password", _password);
            params.put("query", queryFilters);
            params.put("udfs", _config.getUdfs());
            params.put("start", String.valueOf(_startRecord));
            params.put("limit", String.valueOf(_limit));

            StringEntity requestBody = new StringEntity(params.toString());
            requestBody.setContentType("application/json");
            post.addHeader("content-type", "application/json");
            post.setEntity(requestBody);

            ResponseHandler<String> handler = new BasicResponseHandler();
            HttpResponse response = client.execute(post);
            StatusLine status = response.getStatusLine();

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
                return new ExportSamplesResponse(_export, handler.handleResponse(response), status.getStatusCode(), job);
            else
                return new ExportSamplesResponse(_export, status.getReasonPhrase(), status.getStatusCode(), job);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
