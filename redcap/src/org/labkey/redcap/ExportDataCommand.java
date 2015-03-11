package org.labkey.redcap;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.message.BasicNameValuePair;
import org.labkey.redcap.RedcapCommandResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: 4/16/13
 */
public class ExportDataCommand
{
    String _url;
    String _token;

    public ExportDataCommand(String url, String token)
    {
        _url = url;
        _token = token;
    }

    public RedcapCommandResponse execute(HttpClient client)
    {
        HttpPost post = new HttpPost(_url);

        try {
            List<org.apache.http.NameValuePair> params = new ArrayList<org.apache.http.NameValuePair>();

            params.add(new BasicNameValuePair("token", _token));
            params.add(new BasicNameValuePair("content", "record"));
            params.add(new BasicNameValuePair("format", "csv"));
            params.add(new BasicNameValuePair("type", "flat"));
            params.add(new BasicNameValuePair("rawOrLabel", "both"));

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
