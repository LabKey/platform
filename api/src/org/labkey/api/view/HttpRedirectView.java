/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

import java.io.PrintWriter;

/**
 * A view that simple redirects the browser to some other URL.
 * @see HttpPostRedirectView
 * User: matthewb
 * Date: 2011-11-09
 */
public class HttpRedirectView extends HttpView
{
    final String _url;

    public HttpRedirectView(String url)
    {
        _url = url;
    }

    @Override
    public View getView()
    {
        return new RedirectView(_url, false);
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        throw new RedirectException(_url);
    }
}
