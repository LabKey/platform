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
 * Created by klum on 5/24/2014.
 */
public class ExportLocationCommand
{
    private String _url;
    private String _username;
    private String _password;
    private String _locationId;

    public ExportLocationCommand(String url, String username, String password, String locationId)
    {
        _url = url;
        _username = username;
        _password = password;
        _locationId = locationId;
    }

    public FreezerProCommandResonse execute(HttpClient client, PipelineJob job)
    {
        HttpPost post = new HttpPost(_url);

        try {
            List<NameValuePair> params = new ArrayList<NameValuePair>();

            params.add(new BasicNameValuePair("method", "location_info"));
            params.add(new BasicNameValuePair("username", _username));
            params.add(new BasicNameValuePair("password", _password));
            params.add(new BasicNameValuePair("id", _locationId));

            post.setEntity(new UrlEncodedFormEntity(params));

            ResponseHandler<String> handler = new BasicResponseHandler();

            HttpResponse response = client.execute(post);
            StatusLine status = response.getStatusLine();

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
                return new ExportLocationResponse(handler.handleResponse(response), status.getStatusCode(), job);
            else
                return new ExportLocationResponse(status.getReasonPhrase(), status.getStatusCode(), job);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
