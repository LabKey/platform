package org.labkey.api.sequenceanalysis.pipeline;

import java.io.File;

/**
 * Created by bimber on 9/15/2014.
 */
public interface InputReads
{
    public File getFirstFastq();

    public File getSecondFastq();

    public boolean isPairedEnd();
}
