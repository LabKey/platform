package org.labkey.study.writer;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.study.xml.StudyDocument;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 10:00:46 AM
 */
public class ExportContext
{
    private User _user;
    private Container _c;
    private StudyDocument _studyDoc;
    private boolean _locked = false;

    public ExportContext(User user, Container c)
    {
        _user = user;
        _c = c;
        _studyDoc = StudyXmlWriter.getStudyDocument();
    }

    public User getUser()
    {
        return _user;
    }

    public Container getContainer()
    {
        return _c;
    }

    // Full study doc -- only interesting to StudyXmlWriter
    public StudyDocument getStudyDocument()
    {
        if (_locked)
            throw new IllegalStateException("Can't access StudyDocument after study.xml has been written");

        return _studyDoc;
    }

    // Study node -- interesting to any top-level writer that needs to set info into study.xml
    public StudyDocument.Study getStudyXml()
    {
        return getStudyDocument().getStudy();
    }

    public void lockStudyDocument()
    {
        _locked = true;
    }
}
