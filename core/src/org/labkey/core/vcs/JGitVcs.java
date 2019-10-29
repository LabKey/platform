package org.labkey.core.vcs;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.vcs.Vcs;
import org.labkey.api.vcs.VcsService;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

class JGitVcs implements Vcs
{
    private static final Logger LOG = Logger.getLogger(JGitVcs.class);
    private final Repository _repository;
    private final Git _git;
    private final CredentialsProvider _credentialsProvider;


    /** return null if dir belongs to a git repository, return path to root if it is */
    static public File isGitRepository(File dir)
    {
        try
        {
            JGitVcs v = new JGitVcs(dir);
            return v._repository.getDirectory();
        }
        catch (IOException x)
        {
            return null;
        }
    }


    JGitVcs(File rootDirectory) throws IOException
    {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        _repository = builder
                .readEnvironment()
                .findGitDir(rootDirectory)
                .build();

        // try to find credentials
        // CONSIDER use labkey secure properties?
        String user = null, password = null;

        File home = new File(System.getProperty("user.home"));
        File credentialsFile = new File(home,".git-credentials");
        if (credentialsFile.isFile())
        {
            var lines = FileUtils.readLines(credentialsFile, "UTF-8");
            for (var line : lines)
            {
                line = trimToEmpty(line);
                if (startsWith(line, "#"))
                    continue;
                try
                {
                    URL url = new URL(line);
                    var userInfo = StringUtils.split(url.getUserInfo(),':');
                    if (userInfo.length == 2)
                    {
                        user = PageFlowUtil.decode(userInfo[0]);
                        password = PageFlowUtil.decode(userInfo[1]);
                    }
                }
                catch (MalformedURLException x)
                {
                    // pass
                }
            }
        }
        _credentialsProvider = null==user || null==password ? null : new UsernamePasswordCredentialsProvider(user,password);
        _git = new Git(_repository);
    }

    @Override
    public File getRepositoryRoot() throws IOException
    {
        File dir = _repository.getDirectory();
        if (null != dir && ".git".equals(dir.getName()))
            dir = dir.getParentFile();
        return dir;
    }

    @Override
    public String getRemoteUrl() throws IOException
    {
        try
        {
            List<RemoteConfig> remotes = _git.remoteList().call();
            for (var r : remotes)
            {
                if ("origin".equals(r.getName()))
                {
                    List<URIish> l = r.getURIs();
                    return l.size() > 0 ? l.get(0).toString() : null;
                }
            }
            return null;
        }
        catch (GitAPIException x)
        {
            throw new IOException(x);
        }
    }

    @Override
    public String getBranch() throws IOException
    {
        final Ref head = _repository.exactRef(Constants.HEAD);
        if (head != null && head.isSymbolic())
        {
            String branch = Repository.shortenRefName(head.getLeaf().getName());
            return branch;
        }
        return null;
    }

    @Override
    public void addFile(File file) throws IOException
    {
        if (!file.isFile())
            throw new IOException(file.getPath() + " is not a file");
        _git.add().addFilepattern(file.getPath());
    }


    @Override
    public void moveFile(File file, File destinationDirectory) throws IOException
    {
        if (!file.isFile())
            throw new IOException(file.getPath() + " is not a file");
        if (!destinationDirectory.isDirectory())
            throw new IOException(destinationDirectory.getPath() + " is not a directory");
        File target = new File(destinationDirectory, file.getName());
        Files.move(file.toPath(), target.toPath(), ATOMIC_MOVE);
        try
        {
            _git.add().addFilepattern(target.getPath()).call();
            _git.rm().addFilepattern(file.getPath()).call();
        }
        catch (GitAPIException e)
        {
            throw new RuntimeException(e);
        }
    }

    public VcsService.VcsStatus status() throws IOException
    {
        try
        {
            return new GitStatusWrapper(_git.status().call());
        }
        catch (GitAPIException x)
        {
            throw new IOException(x);
        }
    }


    public static class GitStatusWrapper implements VcsService.VcsStatus
    {
        final Status _delegate;

        GitStatusWrapper(Status s)
        {
            _delegate = s;
        }

        @Override
        public boolean isClean()
        {
            return _delegate.isClean();
        }

        @Override
        public boolean hasUncommittedChanges()
        {
            return _delegate.hasUncommittedChanges();
        }

        @Override
        public Set<String> getAdded()
        {
            return _delegate.getAdded();
        }

        @Override
        public Set<String> getChanged()
        {
            return _delegate.getChanged();
        }

        @Override
        public Set<String> getRemoved()
        {
            return _delegate.getRemoved();
        }

        @Override
        public Set<String> getMissing()
        {
            return _delegate.getMissing();
        }

        @Override
        public Set<String> getModified()
        {
            return _delegate.getModified();
        }

        @Override
        public Set<String> getUntracked()
        {
            return _delegate.getUntracked();
        }

        @Override
        public Set<String> getUntrackedFolders()
        {
            return _delegate.getUntrackedFolders();
        }

        @Override
        public Set<String> getConflicting()
        {
            return _delegate.getConflicting();
        }

        @Override
        public Set<String> getIgnoredNotInIndex()
        {
            return _delegate.getIgnoredNotInIndex();
        }

        @Override
        public Set<String> getUncommittedChanges()
        {
            return _delegate.getUncommittedChanges();
        }
    }
}
