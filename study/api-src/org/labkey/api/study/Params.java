package org.labkey.api.study;

import org.labkey.api.util.Pair;
import org.labkey.api.util.SafeToRenderEnum;

public enum Params implements SafeToRenderEnum
{
    cohortFilterType
    {
        @Override
        public void apply(Config c, Pair<String, String> entry)
        {
            c.setType(entry.getValue());
        }
    },
    cohortId
    {
        @Override
        public void apply(Config c, Pair<String, String> entry)
        {
            c.setCohortId(entry.getValue());
        }
    },
    cohortEnrolled
    {
        @Override
        public void apply(Config c, Pair<String, String> entry)
        {
            c.setEnrolled(entry.getValue());
        }
    };

    public abstract void apply(Config c, Pair<String, String> value);
}
