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
                try
                {
                    return new JGitVcs(rootDirectory);
                }
                catch (IOException x)
                {
                    return null;
                }
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
            File repos = JGitVcs.isGitRepository(dir);
            if (null != repos)
                return git;

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

}
