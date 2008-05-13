/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.demo.view;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ActionURL;
import org.labkey.demo.model.DemoManager;

import javax.servlet.ServletException;
import java.sql.SQLException;

/**
 * User: brittp
 * Date: Jan 23, 2006
 * Time: 12:59:21 PM
 */
public class DemoWebPart extends JspView<BulkUpdatePage>
{
    static Logger _log = Logger.getLogger(DemoWebPart.class);

    public DemoWebPart()
    {
        this(HttpView.currentContext().getContainer());
    }


    public DemoWebPart(Container c)
    {
        super("/org/labkey/demo/view/demoWebPart.jsp", new BulkUpdatePage());
        setTitle("Demo Summary");
        setTitleHref(new ActionURL("demo", "begin", HttpView.currentContext().getContainer()));
    }


    protected void prepareWebPart(BulkUpdatePage model) throws ServletException
    {
        super.prepareWebPart(model);
        
        try
        {
            if (model.getList().isEmpty())
                model.setList(DemoManager.getInstance().getPeople(getViewContext().getContainer()));
        }
        catch (SQLException e)
        {
            _log.error("Error retrieving list of people.", e);
        }
    }
}
