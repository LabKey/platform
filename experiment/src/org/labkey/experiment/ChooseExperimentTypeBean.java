package org.labkey.experiment;

import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.ExperimentRunFilter;

import java.util.Set;

/**
 * User: jeckels
 * Date: Oct 11, 2006
 */
public class ChooseExperimentTypeBean
{
    private final Set<ExperimentRunFilter> _filters;
    private final ExperimentRunFilter _selectedFilter;
    private final ActionURL _url;

    public ChooseExperimentTypeBean(Set<ExperimentRunFilter> filters, ExperimentRunFilter selectedFilter, ActionURL url)
    {
        _filters = filters;
        if (selectedFilter == null)
        {
            _selectedFilter = ExperimentRunFilter.ALL_RUNS_FILTER;
        }
        else
        {
            _selectedFilter = selectedFilter;
        }

        _url = url;
    }

    public Set<ExperimentRunFilter> getFilters()
    {
        return _filters;
    }

    public ActionURL getUrl()
    {
        return _url;
    }

    public ExperimentRunFilter getSelectedFilter()
    {
        return _selectedFilter;
    }
}
