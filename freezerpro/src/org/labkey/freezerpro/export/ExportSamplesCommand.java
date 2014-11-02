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

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.message.BasicNameValuePair;
import org.labkey.api.pipeline.PipelineJob;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by klum on 5/21/2014.
 */
public class ExportSamplesCommand
{
    private FreezerProExport _export;
    private String _url;
    private String _username;
    private String _password;
    private String _searchFilterString;
    private int _startRecord;
    private int _limit;

    public ExportSamplesCommand(FreezerProExport export, String url, String username, String password, String searchFilterString, int startRecord, int limit)
    {
        _export = export;
        _url = url;
        _username = username;
        _password = password;
        _searchFilterString = searchFilterString;
        _startRecord = startRecord;
        _limit = limit;
    }

    public FreezerProCommandResonse execute(HttpClient client, PipelineJob job)
    {
        HttpPost post = new HttpPost(_url);

        try {
            List<NameValuePair> params = new ArrayList<NameValuePair>();

            params.add(new BasicNameValuePair("method", "search_samples"));
            params.add(new BasicNameValuePair("username", _username));
            params.add(new BasicNameValuePair("password", _password));

            if (_searchFilterString != null)
                params.add(new BasicNameValuePair("query", _searchFilterString));
            else
                params.add(new BasicNameValuePair("query", ""));

            params.add(new BasicNameValuePair("start", String.valueOf(_startRecord)));
            params.add(new BasicNameValuePair("limit", String.valueOf(_limit)));

            post.setEntity(new UrlEncodedFormEntity(params));

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
