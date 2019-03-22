/*
 * Copyright (c) 2007-2012 LabKey Corporation
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
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: matthewb
 * Date: Dec 7, 2006
 */
public class DefaultModelAndView<ModelBean> extends ModelAndView
{
    View _view;
    ModelBean _model;

    public DefaultModelAndView()
    {
    }

    public DefaultModelAndView(View v, ModelBean m)
    {
        super(v);
        _view = v;
        _model = m;
    }

    public void setView(View view)
    {
        _view = view;
    }

    protected void setModelBean(ModelBean model)
    {
        _model = model;
    }

    public View getView()
    {
        return _view;
    }

    public ModelBean getModelBean()
    {
        return _model;
    }

    public void render(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        _view.render(getModel(), request, response);
    }
}
