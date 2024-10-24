/*
 * Copyright (c) 2010 LabKey Corporation
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

package gwt.client.org.labkey.plate.designer.client;

import gwt.client.org.labkey.plate.designer.client.model.GWTWellGroup;

/**
 * User: jeckels
 * Date: Apr 19, 2007
 */
public class WellGroupPropertyPanel extends PropertyPanel implements GroupChangeListener
{
    public WellGroupPropertyPanel(TemplateView view)
    {
        super(view);
        view.addGroupListener(this);
        activeGroupChanged(null, null);
    }

    @Override
    public void activeGroupChanged(GWTWellGroup previouslyActive, GWTWellGroup currentlyActive)
    {
        if (previouslyActive != null)
        {
            previouslyActive.setProperties(getProperties());
        }
        if (currentlyActive == null)
        {
            redraw("No well group selected.");
        }
        else
        {
            redraw(currentlyActive.getProperties());
        }
    }

    @Override
    public void activeGroupTypeChanged(String type)
    {
    }

    @Override
    public void groupAdded(GWTWellGroup group)
    {
    }

    @Override
    public void groupRemoved(GWTWellGroup group)
    {
    }

}
