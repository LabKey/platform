/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

abstract class AbstractViewBox extends WebPartView<Object>
{
    protected final List<ModelAndView> _views;

    public AbstractViewBox(ModelAndView... views)
    {
        super(WebPartView.FrameType.NONE);
        _views = new ArrayList<>(Arrays.asList(views));
    }

    @Override
    public boolean isVisible()
    {
        return null != _views && !_views.isEmpty();
    }

    public void addView(ModelAndView v)
    {
        if (null == v)
            return;
        _views.add(v);
    }

    public void addView(DOM.Renderable r)
    {
        if (null == r)
            return;
        _views.add(new HtmlView(r));
    }

    public void addView(ModelAndView v, int index)
    {
        if (null == v)
            return;
        _views.add(index, v);
    }

    @Override
    public List<ModelAndView> getViews()
    {
        return new ArrayList<>(_views);
    }
}
