package org.labkey.api.view.template;

import java.util.Collection;

public interface Warnings
{
    static Warnings of(Collection<String> collection)
    {
        return new Warnings()
        {
            @Override
            public void add(String warning)
            {
                collection.add(warning);
            }
        };
    }

    void add(String warning);
}
