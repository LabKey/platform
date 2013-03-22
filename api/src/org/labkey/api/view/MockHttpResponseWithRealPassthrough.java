package org.labkey.api.view;

import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletResponse;

/**
 * A mock version of a response that holds a reference to a "real" response. This is useful because it
 * lets us buffer our response but also send content to the client as a ping to make sure that it's still connected.
 * User: jeckels
 * Date: 3/21/13
 */
public class MockHttpResponseWithRealPassthrough extends MockHttpServletResponse
{
    private final HttpServletResponse _response;

    public MockHttpResponseWithRealPassthrough(HttpServletResponse response)
    {
        _response = response;
    }

    public HttpServletResponse getResponse()
    {
        return _response;
    }
}
