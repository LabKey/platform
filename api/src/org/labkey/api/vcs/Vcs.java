package org.labkey.api.vcs;

import java.io.File;

public interface Vcs
{
    void addFile(String file);
    void deleteFile(String file);
    void moveFile(File file, File destinationDirectory);
}
