package org.labkey.api.vcs;

import java.io.File;
import java.io.IOException;

public interface Vcs
{
    File getRepositoryRoot() throws IOException;
    String getRemoteUrl() throws IOException;
    String getBranch() throws IOException;
    void addFile(File file) throws IOException;
    void moveFile(File file, File destinationDirectory) throws IOException;
    VcsService.VcsStatus status() throws IOException;
}