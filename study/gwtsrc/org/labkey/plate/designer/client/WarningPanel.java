package org.labkey.plate.designer.client;

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
