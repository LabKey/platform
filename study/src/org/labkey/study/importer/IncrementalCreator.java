package org.labkey.study.importer;

import org.labkey.api.writer.VirtualFile;

/**
 * User: adam
 * Date: 1/19/13
 * Time: 8:37 AM
 */
public class IncrementalCreator
{
    static void generateIncrementalArchive(VirtualFile vf, String fileName)
    {
        if (!"specimen.tsv".equals(fileName))
            return;

        // Code to generate specimen archive diff goes here
    }
}
