package org.labkey.di.filters;

import org.labkey.api.data.SimpleFilter;
import org.labkey.di.VariableMap;

/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 4/22/13
 * Time: 11:55 AM
 */
public interface FilterStrategy
{
    boolean hasWork();

    /* Side effect of setting parameters */
    SimpleFilter getFilter(VariableMap variables);
}