package org.labkey.experiment;

import org.labkey.api.exp.ExperimentRunFilter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.view.JspView;

import java.util.Set;

/**
 * User: jeckels
 * Date: Feb 26, 2008
 */
public class RunTypeWebPart extends JspView<Set<ExperimentRunFilter>>
{
    public static final String WEB_PART_NAME = "Run Types";

    public RunTypeWebPart()
    {
        super("/org/labkey/experiment/runTypeFilters.jsp", ExperimentService.get().getExperimentRunFilters());
        setTitle("Run Types");
    }
}
