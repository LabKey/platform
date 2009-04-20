package org.labkey.study.writer;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 4:17:59 PM
 */
public interface Writer<T>
{
    public void write(T object, ExportContext ctx) throws Exception;
}
