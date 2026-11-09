/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.wiki;

import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.WebPartView;
import org.labkey.api.writer.HtmlWriter;

import static org.labkey.api.util.DOM.Attribute.cellpadding;
import static org.labkey.api.util.DOM.Attribute.colspan;
import static org.labkey.api.util.DOM.Attribute.width;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;

public class LinkBarView extends WebPartView<Object>
{
    private final NavTree[] _links;
    private boolean _drawLine = false;

    public LinkBarView(NavTree... links)
    {
        super(FrameType.DIV);
        _links = links;
    }

    public void setDrawLine(boolean fDrawLine)
    {
        _drawLine = fDrawLine;
    }

    @Override
    protected void renderView(Object model, HtmlWriter out)
    {
        TABLE(
            at(width, "100%", cellpadding, 0),
            TR(
                TD(
                    (DOM.Renderable) ret -> {
                        for (NavTree link : _links)
                        {
                            out.write(LinkBuilder.labkeyLink(link.getText(), link.getHref()));
                            out.write(HtmlString.NBSP);
                        }
                        return ret;
                    }
                )
            ),
            _drawLine ? TR(TD(at(colspan, 3).cl("labkey-title-area-line"))) : null
        ).appendTo(out);
    }
}
