package org.labkey.api.data;

import org.labkey.api.util.URLHelper;
import org.labkey.api.view.DisplayElement;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Apr 7, 2010
 * Time: 10:15:25 AM
 */

/**
 * Represents configuration information for a specific button in a button bar configuration.
 * Currently this is used only with the QueryWebPart
 */
public interface ButtonConfig
{
    public DisplayElement createButton(List<DisplayElement> originalButtons);
}
