package org.labkey.api.study.assay;

import org.labkey.api.query.ExprColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Container;

import java.sql.Types;

/**
 * User: kevink
 * Date: May 31, 2009 8:42:09 PM
 */
public class StudyDataSetColumn extends ExprColumn
{
    private Container _studyContainer;

    public StudyDataSetColumn(TableInfo parent, String name, SQLFragment sql, Container studyContainer)
    {
        super(parent, name, sql, Types.INTEGER);
        _studyContainer = studyContainer;
    }

    public Container getStudyContainer()
    {
        return _studyContainer;
    }
}
