package org.labkey.api.sequenceanalysis;

import java.io.File;

/**
 * Created by bimber on 7/28/2014.
 */
public interface ReferenceLibraryHelper
{
    public File getReferenceFasta();

    public File getIdKeyFile();

    public File getIndexFile();

    public Integer resolveSequenceId(String refName);
}
