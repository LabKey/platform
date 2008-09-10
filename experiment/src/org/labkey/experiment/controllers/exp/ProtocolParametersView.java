/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.controllers.exp;

import org.labkey.api.view.JspView;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;

import java.sql.SQLException;

/**
 * User: jeckels
* Date: Dec 19, 2007
*/
public class ProtocolParametersView extends JspView
{
    public ProtocolParametersView(ExpProtocol protocol) throws SQLException
    {
        super("/org/labkey/experiment/Parameters.jsp", protocol.getProtocolParameters());
        setTitle("Protocol Parameters");
    }
}
