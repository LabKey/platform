package org.labkey.api.vcs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public interface VcsService
{
    static @NotNull VcsService get()
    {
        return Objects.requireNonNull(ServiceRegistry.get().getService(VcsService.class));
    }

    static void setInstance(VcsService instance)
    {
        ServiceRegistry.get().registerService(VcsService.class, instance);
    }

    /**
     * Return the appropriate Vcs implementation if the directory is under version control
     * @param directory Directory to test
     * @return The corresponding Vcs implementation or null
     */
    @Nullable Vcs getVcs(File directory);

    // interface and wrapper of org.eclipse.jgit.api.Status
    // https://git.eclipse.org/c/jgit/jgit.git/tree/org.eclipse.jgit/src/org/eclipse/jgit/api/Status.java
    public interface VcsStatus
    {
        boolean isClean();
        boolean hasUncommittedChanges();
        Set<String> getAdded();
        Set<String> getChanged();
        Set<String> getRemoved();
        Set<String> getMissing();
        Set<String> getModified();
        Set<String> getUntracked();
        Set<String> getUntrackedFolders();
        Set<String> getConflicting();
        //Map<String, IndexDiff.StageState> getConflictingStageState();
        Set<String> getIgnoredNotInIndex();
        Set<String> getUncommittedChanges();
    }


}
