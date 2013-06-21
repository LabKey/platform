package org.labkey.di.filters;

import org.labkey.api.data.SimpleFilter;
import org.labkey.di.VariableMap;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.steps.StepMeta;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 6/20/13
 * Time: 2:16 PM
 */
public class SelectAllFilterStrategy implements FilterStrategy
{
    @Override
    public boolean hasWork()
    {
        return true;
    }

    @Override
    public SimpleFilter getFilter(VariableMap variables)
    {
        return null;
    }

    public static class Factory implements FilterStrategy.Factory
    {
        @Override
        public FilterStrategy getFilterStrategy(TransformJobContext context, StepMeta stepMeta)
        {
            return new SelectAllFilterStrategy();
        }

        @Override
        public boolean checkStepsSeparately()
        {
            return false;
        }
    }
}
