package org.labkey.study.view;

import org.labkey.study.model.*;
import org.labkey.api.jsp.JspBase;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.sql.SQLException;

/**
 * User: brittp
 * Date: Jan 11, 2006
 * Time: 11:27:26 AM
 */
public abstract class BaseStudyPage extends JspBase
{
    private Study _study;

    protected Study getStudy()
    {
//        if (_study == null)
//            throw new IllegalStateException("init must be called before rendering this JSP.");
        return _study;
    }

    protected Visit[] getVisits()
    {
        return getStudy().getVisits();
    }

    protected DataSetDefinition[] getDataSets()
    {
        return getStudy().getDataSets();
    }

    protected Site[] getSites() throws SQLException
    {
        return getStudy().getSites();
    }

    protected Cohort[] getCohorts(User user) throws SQLException
    {
        return getStudy().getCohorts(user);
    }

    public void init(Container c)
    {
        _study = StudyManager.getInstance().getStudy(c);
    }

    public void init(Study study)
    {
        _study = study;
    }
}
