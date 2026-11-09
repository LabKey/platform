/*
 * Copyright (c) 2004-2026 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.util.DOM;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.writer.HtmlWriter;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.labkey.api.util.DOM.Attribute.align;
import static org.labkey.api.util.DOM.Attribute.valign;
import static org.labkey.api.util.DOM.Attribute.width;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;

/** Lays out child {@link ModelAndView} horizontally */
public class HBox extends AbstractViewBox
{
    private final Map<ModelAndView, String> _widths = new HashMap<>();

    private String _tableWidth = "100%";

    public HBox(ModelAndView... views)
    {
        super(views);
    }

    public void setTableWidth(String width)
    {
        _tableWidth = width;
    }

    public void addView(ModelAndView v, String width)
    {
        super.addView(v);
        _widths.put(v, width);
    }

    @Override
    protected void renderView(Object model, HtmlWriter out) throws Exception
    {
        if (_views != null && !_views.isEmpty())
        {
            TABLE(
                at(width, _tableWidth),
                TR(
                    _views.stream()
                        .filter(Objects::nonNull)
                        .map(view -> {
                            String w = _widths.get(view);
                            return TD(
                                at(valign, "top", align, "left").at(w != null, width, w),
                                (DOM.Renderable) ret -> {
                                    try
                                    {
                                        include(view);
                                    }
                                    catch (Exception e)
                                    {
                                        throw UnexpectedException.wrap(e);
                                    }
                                    return ret;
                                }
                            );
                        })
                )
            ).appendTo(out);
        }
    }
}
