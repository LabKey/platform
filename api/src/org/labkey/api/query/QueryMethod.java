package org.labkey.api.query;

import org.labkey.api.data.SQLFragment;

abstract public class QueryMethod
{
    abstract public SQLFragment getSql();
}
