/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.redcap;

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
import org.apache.http.util.EntityUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: 4/19/13
 */
public class ExportEventsCommand
{
    String _url;
    String _token;

    public ExportEventsCommand(String url, String token)
    {
        _url = url;
        _token = token;
    }

    public RedcapCommandResponse execute(HttpClient client)
    {
        HttpPost post = new HttpPost(_url);

        try {
            List<NameValuePair> params = new ArrayList<NameValuePair>();

            params.add(new BasicNameValuePair("token", _token));
            params.add(new BasicNameValuePair("content", "event"));
            params.add(new BasicNameValuePair("format", "csv"));

            post.setEntity(new UrlEncodedFormEntity(params));

            ResponseHandler<String> handler = new BasicResponseHandler();

            HttpResponse response = client.execute(post);
            StatusLine status = response.getStatusLine();

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
                return new RedcapCommandResponse(handler.handleResponse(response), status.getStatusCode());
            else
            {
                EntityUtils.consume(response.getEntity());
                return new RedcapCommandResponse(status.getReasonPhrase(), status.getStatusCode());
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

}
