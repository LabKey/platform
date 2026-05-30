/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.api.action;

import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.view.ViewContext;

public class LabKeyErrorWithHtml extends LabKeyError
{
    private final HtmlString _html;

    public LabKeyErrorWithHtml(String message, HtmlString html)
    {
        super(message);
        _html = html;
    }

    @Override
    public HtmlString renderToHTML(ViewContext context)
    {
        HtmlStringBuilder builder = HtmlStringBuilder.of(super.renderToHTML(context));
        builder.append(_html);

        return builder.getHtmlString();
    }

    public HtmlString getHtml()
    {
        return _html;
    }
}
