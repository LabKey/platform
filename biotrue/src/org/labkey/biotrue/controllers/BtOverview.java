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

package org.labkey.biotrue.controllers;

import org.labkey.api.view.Overview;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.biotrue.objectmodel.BtServer;

public class BtOverview extends Overview
{
    public BtOverview(User user, Container container)
    {
        super(user, container);
        setTitle("BioTrue Connector Dashboard");
        setExplanatoryHTML("The BioTrue connector allows periodically walking a BioTrue CDMS, and copying the files down to a file system.");
        BtServer[] servers = BtServer.getForContainer(container);
        Step step = new Step("Define Server", servers.length == 0 ? Step.Status.required : Step.Status.completed);
        if (servers.length == 0)
        {
            step.setStatusHTML("There are no servers defined in this folder.");
        }
        else
        {
            if (servers.length == 1)
            {
                step.setStatusHTML("There is <a href=\"" + h(servers[0].detailsURL()) + "\">one server</a> defined in this folder.");
            }
            else
            {
                step.setStatusHTML("There are <a href=\"" + h(BtController.Action.showServers.url(getContainer())) + "\">" +
                        servers.length + " servers</a> defined in this folder.");
            }
        }
        step.addAction(new Action("Define new server", BtController.Action.newServer.url(getContainer())));
        addStep(step);
    }


}
