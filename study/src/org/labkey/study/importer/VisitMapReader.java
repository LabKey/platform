package org.labkey.study.importer;

import java.util.List;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 8:41:34 PM
 */
public interface VisitMapReader
{
    List<VisitMapRecord> getRecords(String content);
}
