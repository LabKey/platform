package org.labkey.api.data;

import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.LookupURLExpression;

import java.util.Set;
import java.io.Writer;
import java.io.IOException;

public class DetailsColumn extends UrlColumn
{
    public DetailsColumn(StringExpressionFactory.StringExpression urlExpression)
    {
        super(urlExpression, "details");
    }
}

