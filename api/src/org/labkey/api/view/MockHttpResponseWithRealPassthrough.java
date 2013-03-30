/*
 * Copyright (c) 2013 LabKey Corporation
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
