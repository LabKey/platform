package org.labkey.core.vcs;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.vcs.Vcs;
import org.labkey.api.vcs.VcsService;

import java.io.File;
import java.io.IOException;

public class VcsServiceImpl implements VcsService
{
    private static final Logger LOG = Logger.getLogger(VcsServiceImpl.class);

    @Override
    public @Nullable Vcs getVcs(File directory)
    {
        return VcsType.determine(directory).getVcs(directory);
    }

    private enum VcsType
    {
        git()
        {
            @Override
            Vcs getVcs(File rootDirectory)
            {
                return new GitVcs(rootDirectory);
            }
        },
        svn()
        {
            @Override
            Vcs getVcs(File rootDirectory)
            {
                return null;  // NYI
            }
        },
        none()
        {
            @Override
            Vcs getVcs(File rootDirectory)
            {
                return null;
            }
        };

        private static VcsType determine(File dir)
        {
            while (null != dir)
            {
                if (new File(dir, ".git").exists())
                    return git;
                if (new File(dir, ".svn").exists())
                    return svn;
                dir = dir.getParentFile();
            }

            return none;
        }

        abstract Vcs getVcs(File rootDirectory);
    }

    private static class GitVcs implements Vcs
    {
        private final File _rootDirectory;

        private GitVcs(File rootDirectory)
        {
            _rootDirectory = rootDirectory;
        }

        @Override
        public void addFile(String file)
        {
            execute("git", "add", file);
        }

        @Override
        public void deleteFile(String file)
        {
            execute("git", "rm", file);
        }

        @Override
        public void moveFile(File file, File destinationDirectory)
        {
            execute("git", "mv", file.getName(), destinationDirectory.getAbsolutePath());
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
}
