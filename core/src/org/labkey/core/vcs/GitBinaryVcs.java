package org.labkey.core.vcs;

import org.apache.log4j.Logger;
import org.labkey.api.vcs.Vcs;
import org.labkey.api.vcs.VcsService;

import java.io.File;
import java.io.IOException;

class GitBinaryVcs implements Vcs
{
    private static final Logger LOG = Logger.getLogger(GitBinaryVcs.class);
    private final File _rootDirectory;

    GitBinaryVcs(File rootDirectory)
    {
        _rootDirectory = rootDirectory;
    }

    @Override
    public File getRepositoryRoot() throws IOException
    {
        return null;
    }

    @Override
    public String getRemoteUrl() throws IOException
    {
        return null;
    }

    @Override
    public String getBranch() throws IOException
    {
        return null;
    }

    @Override
    public void addFile(File file)
    {
        execute("git", "add", file.getPath());
    }

    @Override
    public void moveFile(File file, File destinationDirectory)
    {
        execute("git", "mv", file.getName(), destinationDirectory.getAbsolutePath());
    }

    @Override
    public VcsService.VcsStatus status() throws IOException
    {
        throw new UnsupportedOperationException("status");
    }

    private String log(File dir, String... command)
    {
        String cl = String.join(" ", command);
        LOG.info(dir + ": " + cl);

        return cl;
    }

    private void execute(String... command)
    {
        String cl = log(_rootDirectory, command);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(_rootDirectory);
        try
        {
            Process p = builder.start();
            int err = p.waitFor();
            if (0 != err)
                throw new RuntimeException("Error code attempting to " + cl + ": " + err);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException("Interrupted while executing command " + cl, e);
        }
        catch (IOException e)
        {
            throw new RuntimeException("IOException while executing command " + cl, e);
        }
    }
}
