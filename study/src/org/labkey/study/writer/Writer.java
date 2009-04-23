package org.labkey.study.writer;

import org.labkey.api.util.VirtualFile;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 4:17:59 PM
 */
public interface Writer<T>
{
    public void write(T object, ExportContext ctx, VirtualFile fs) throws Exception;
}
