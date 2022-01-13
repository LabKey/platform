/*
 * Copyright (c) 2009-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.importer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.admin.StaticLoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Study;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

/*
* User: adam
* Date: Jun 10, 2009
* Time: 5:36:01 PM
*/
public class StudyReload
{
    private static final Logger LOG = LogManager.getLogger(StudyReload.class);

    private static String getDescription(Study study)
    {
        return study != null ? study.getLabel() : "";
    }

    @NotNull
    public static PipeRoot getPipelineRoot(Container c)
    {
        PipeRoot root = PipelineService.get().findPipelineRoot(c);
        if (root == null || !root.isValid())
        {
            throw new NotFoundException("No valid pipeline root found");
        }
        return root;
    }

    private static final String STUDY_LOAD_FILENAME = "studyload.txt";

    public static StudyImpl validateStudyForReload(Container c) throws ImportException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(c);

        if (null == study)
        {
            // Study must have been deleted... but if so, timer should have been disabled
            throw new ImportException("Study does not exist in folder " + c.getPath());
        }
        else
        {
            return study;
        }
    }

    public static PipeRoot validatePipeRoot(Container c) throws ImportException
    {
        PipeRoot root = PipelineService.get().findPipelineRoot(c);

        if (null == root)
            throw new ImportException("Pipeline root is not set in folder " + c.getPath());

        if (!root.isValid())
            throw new ImportException("Pipeline root does not exist in folder " + c.getPath());

        return root;
    }

    public static ReloadStatus attemptTriggeredReload(ImportOptions options, String source) throws ImportException
    {
        Container c = ContainerManager.getForId(options.getContainerId());
        if (null == c)
            throw new ImportException("Container " + options.getContainerId() + " does not exist");
        else
        {
            StudyImpl study = validateStudyForReload(c);
            if (study == null)
                throw new ImportException("Reload failed. Study doesn't exist.");

            return reloadStudy(study, options, source, null);
        }
    }

    public static ReloadStatus attemptReload(ImportOptions options, String source) throws ImportException
    {
        Container c = ContainerManager.getForId(options.getContainerId());

        if (null == c)
        {
            // Container must have been deleted
            throw new ImportException("Container " + options.getContainerId() + " does not exist");
        }
        else
        {
            StudyImpl study = validateStudyForReload(c);
            if (study == null)
                throw new ImportException("Reload failed. Study doesn't exist.");

            PipeRoot root = validatePipeRoot(c);
            File studyload = root.resolvePath(STUDY_LOAD_FILENAME);

            if (studyload != null && studyload.isFile())
            {
                Long lastModified = studyload.lastModified();
                Date lastReload = study.getLastReload();

                if (null == lastReload || studyload.lastModified() > (lastReload.getTime() + 1000))  // Add a second since SQL Server rounds datetimes
                {
                    return reloadStudy(study, options, source, lastModified);
                }
            }
            else
            {
                throw new ImportException("Could not find file " + STUDY_LOAD_FILENAME + " in the pipeline root for " + getDescription(study));
            }
        }

        return new ReloadStatus("Reload failed");
    }

    private static void userCanReloadStudy(User user, StudyImpl study)
    {
        if (user == null || !user.isActive())
        {
            throw new UnauthorizedException("Reload user is inactive");
        }

        if (!study.hasPermission(user, UpdatePermission.class))
        {
            throw new UnauthorizedException("Reload user has insufficient permissions");
        }
    }

    public static ReloadStatus reloadStudy(StudyImpl study, @NotNull ImportOptions options, String source, Long lastModified)
    {
        options.addMessage("Study reload was initiated by " + source);

        User reloadUser = options.getUser();
        userCanReloadStudy(reloadUser, study);

        // Try to add this study to the reload queue; if it's full, wait until next time
        // We could submit reload pipeline jobs directly, but:
        //  1) we need a way to throttle automatic reloads and
        //  2) the initial import steps happen synchronously; they aren't part of the pipeline job

        // TODO: Better throttling behavior (e.g., prioritize studies that check infrequently)

        // Careful: Naive approach would be to offer the container to the queue and set last reload
        // time on the study only if successful. This will introduce a race condition, since the
        // import job and the update are likely to be updating the study at roughly the same time.
        // Instead, we optimistically update the last reload time before offering the container and
        // back out that change if the queue is full.
        study = study.createMutable();
        study.setLastReload(lastModified == null ? new Date() : new Date(lastModified));
        StudyManager.getInstance().updateStudy(null, study);

        StudyManager manager = StudyManager.getInstance();
        Container c = null;

        try
        {
            c = ContainerManager.getForId(options.getContainerId());
            Path studyXml;
            PipeRoot root = StudyReload.getPipelineRoot(c);

            // Task overrides default analysis directory, usually when study.xml is located
            // in a subdirectory underneath the pipeline root
            if (options.getAnalysisDir() != null)
            {
                studyXml = Files.list(options.getAnalysisDir())
                        .filter(p -> p.getFileName().toString().equalsIgnoreCase("study.xml"))
                        .findFirst()
                        .orElse(null);
            }
            else
            {
                studyXml = root.resolveRelativePath("study.xml");
            }

            assert c != null;
            study = manager.getStudy(c);
            //noinspection ThrowableInstanceNeverThrown
            BindException errors = new NullSafeBindException(c, "reload");
            ActionURL manageStudyURL = new ActionURL(StudyController.ManageStudyAction.class, c);

            LOG.info("Handling " + c.getPath());

            // issue 15681: if there is a folder archive instead of a study archive, see if the folder.xml exists to point to the study root dir
            if (studyXml == null || !Files.exists(studyXml))
            {
                Path folderXml = root.resolveToNioPath("folder.xml");
                if (folderXml != null && Files.exists(folderXml))
                {
                    FolderImportContext folderCtx = new FolderImportContext(reloadUser, c, folderXml, null, new StaticLoggerGetter(LOG), null);
                    FolderDocument folderDoc = folderCtx.getDocument();
                    if (folderDoc.getFolder().getStudy() != null && folderDoc.getFolder().getStudy().getDir() != null)
                    {
                        studyXml = root.resolveRelativePath("/" + folderDoc.getFolder().getStudy().getDir() + "/study.xml");
                    }
                }
            }
            if (studyXml != null)
                PipelineService.get().queueJob(new StudyImportJob(c, reloadUser, manageStudyURL, studyXml, studyXml.getFileName().toString(), errors, root, options));
            else
                LOG.error("Study.xml does not exist in the analysis directory or pipeline root.");
        }
        catch (Throwable t)
        {
            String studyDescription = (null != study ? " \"" + getDescription(study) + "\"" : "");
            String folderPath = (null != c ? " in folder " + c.getPath() : "");
            LOG.error("Error while reloading study" + studyDescription + folderPath, t);
        }

        return new ReloadStatus("Reloading " + getDescription(study));
    }


    public static class ReloadStatus
    {
        private final String _message;

        private ReloadStatus(String message)
        {
            _message = message;
        }

        public String getMessage()
        {
            return _message;
        }
    }
}
