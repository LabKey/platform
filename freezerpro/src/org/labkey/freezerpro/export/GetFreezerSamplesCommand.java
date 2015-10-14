/*
 * Copyright (c) 2014-2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
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
 * Returns the samples in the specified freezer id
 */
public class GetFreezerSamplesCommand
{
    private String _url;
    private String _username;
    private String _password;
    private String _typeId;
    private FreezerProExport _export;

    public GetFreezerSamplesCommand(FreezerProExport export, String url, String username, String password, String typeId)
    {
        _export = export;
        _url = url;
        _username = username;
        _password = password;
        _typeId = typeId;
    }

    public FreezerProCommandResponse execute(HttpClient client, PipelineJob job)
    {
        HttpPost post = new HttpPost(_url);

        try {
            List<NameValuePair> params = new ArrayList<NameValuePair>();

            params.add(new BasicNameValuePair("method", "freezer_samples"));
            params.add(new BasicNameValuePair("username", _username));
            params.add(new BasicNameValuePair("password", _password));
            params.add(new BasicNameValuePair("id", _typeId));
            params.add(new BasicNameValuePair("limit", "10000"));

            post.setEntity(new UrlEncodedFormEntity(params));

            ResponseHandler<String> handler = new BasicResponseHandler();

            HttpResponse response = client.execute(post);
            StatusLine status = response.getStatusLine();

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
                return new GetFreezerSamplesResponse(_export, handler.handleResponse(response), status.getStatusCode(), job);
            else
                return new GetFreezerSamplesResponse(_export, status.getReasonPhrase(), status.getStatusCode(), job);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
