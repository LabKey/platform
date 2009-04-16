package org.labkey.study.writer;

import org.labkey.study.model.Study;
import org.labkey.study.model.Visit;

import java.io.File;
import java.io.PrintWriter;
import java.io.FileNotFoundException;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 10:52:38 AM
 */
public abstract class VisitMapWriter
{
    private Study _study;
    private File _file;

    protected VisitMapWriter(Study study, File file)
    {
        _study = study;
        _file = file;
    }

    void write() throws FileNotFoundException
    {
        PrintWriter out = null;

        try
        {
            out = new PrintWriter(_file);
            write(_study.getVisits(),  out);
        }
        finally
        {
            if (null != out)
                out.close();
        }
    }

    abstract protected void write(Visit[] visits, PrintWriter out);
}
