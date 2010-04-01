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

import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.DOM;

import java.util.*;

/**
 * User: brittp
 * Date: Mar 1, 2007
 * Time: 4:52:29 PM
 */
public class WarningPanel extends VerticalPanel
{
    public WarningPanel()
    {
        setWidth("100%");
        update(null);
    }

    public void update(Map cellToWarnings)
    {
        clear();
        if (cellToWarnings != null && !cellToWarnings.isEmpty())
        {
            List keys = new ArrayList(cellToWarnings.keySet());
            Collections.sort(keys);
            for (Iterator cellIt = keys.iterator(); cellIt.hasNext(); )
            {
                String cell = (String) cellIt.next();
                List warnings = (List) cellToWarnings.get(cell);
                Label cellLabel = new Label("Well " + cell + ":");
                DOM.setStyleAttribute(cellLabel.getElement(), "fontWeight", "bold");
                add(cellLabel);
                for (Iterator warnIt = warnings.iterator(); warnIt.hasNext(); )
                {
                    String warning = (String) warnIt.next();
                    Label warningText = new Label(warning);
                    add(warningText);
                    DOM.setStyleAttribute(warningText.getElement(), "paddingLeft", "5px");
                }
            }
        }
        else
        {
            Label header = new Label("No warnings.");
            add(header);
        }
    }
}
