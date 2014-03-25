package org.labkey.query.olap;

import org.labkey.api.action.SpringActionController;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.MetadataElement;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.labkey.query.olap.QubeQuery.QubeExpr;
import static org.labkey.query.olap.QubeQuery.QubeMembersExpr;

/**
 * Created by matthew on 3/13/14.
 */
public class SqlQueryImpl
{
    QubeQuery qq;
    BindException errors;

    public SqlQueryImpl(QubeQuery qq, BindException errors)
    {
        this.qq = qq;
        this.errors = errors;
    }


    public String generateSQL() throws BindException
    {
        throw new UnsupportedOperationException("Not implemented yet, need more metadata!");
    }
}
