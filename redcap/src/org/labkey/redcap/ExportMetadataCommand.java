/*
 * Copyright (c) 2013-2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
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

import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: 4/16/13
 */
public class ExportMetadataCommand
{
    String _url;
    String _token;

    public ExportMetadataCommand(String url, String token)
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
            params.add(new BasicNameValuePair("content", "metadata"));
            params.add(new BasicNameValuePair("format", "csv"));

            post.setEntity(new UrlEncodedFormEntity(params));

            ResponseHandler<String> handler = new BasicResponseHandler();

            HttpResponse response = client.execute(post);
            StatusLine status = response.getStatusLine();

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
                return new RedcapCommandResponse(handler.handleResponse(response), status.getStatusCode());
            else
                return new RedcapCommandResponse(status.getReasonPhrase(), status.getStatusCode());
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}

