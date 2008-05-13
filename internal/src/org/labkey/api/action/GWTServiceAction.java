/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.labkey.api.view.NavTree;
import org.labkey.api.gwt.server.BaseRemoteService;

/**
 * User: jeckels
 * Date: Feb 8, 2008
 */
public abstract class GWTServiceAction extends SimpleViewAction
{
    public ModelAndView getView(Object o, BindException errors) throws Exception
    {
        BaseRemoteService service = createService();
        service.doPost(getViewContext().getRequest(), getViewContext().getResponse());
        return null;
    }

    protected abstract BaseRemoteService createService();

    public NavTree appendNavTrail(NavTree root)
    {
        return null;
    }

}
