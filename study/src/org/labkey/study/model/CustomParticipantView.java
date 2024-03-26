/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.study.model;

import org.labkey.api.data.Transient;
import org.labkey.api.module.Module;
import org.labkey.api.data.Entity;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Path;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: brittp
 * Created: May 9, 2008 5:06:18 PM
 */
public class CustomParticipantView extends Entity
{
    private static final int MODULE_PTID_VIEW_ID = -1;

    private String _title;

    // database
    private Integer _rowId;
    private String _body;

    // module view
    private Module _module;
    private Path _path;

    private boolean _active;

    public static CustomParticipantView create(Module module, Path path)
    {
        CustomParticipantView view = new CustomParticipantView();
        view.setRowId(MODULE_PTID_VIEW_ID);
        view.setActive(true);
        view.setModule(module);
        view._path = path;
        return view;
    }

    public CustomParticipantView()
    {
    }

    public void setModule(Module module)
    {
        _module = module;
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public String getBody()
    {
        return _body;
    }

    public void setBody(String body)
    {
        _body = body;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public boolean isActive()
    {
        return _active;
    }

    public void setActive(boolean active)
    {
        _active = active;
    }

    public boolean isModuleParticipantView()
    {
        return getRowId() != null && getRowId() == MODULE_PTID_VIEW_ID;
    }

    @Transient
    public ModelAndView getView()
    {
        if (null == _module)
            throw new IllegalStateException("module is not set");
        /* NOTE: We can't cache this view in this object, because CustomParticipantView is stored in a cache, and HttpView is mutable (e.g. _viewContext). */
        if (null != _path)
            return ModuleHtmlView.get(_module, _path);
        return new ModuleHtmlView(_module, _title, HtmlString.unsafe(getBody()));
    }
}
