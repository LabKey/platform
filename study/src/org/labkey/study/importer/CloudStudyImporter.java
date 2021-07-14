package org.labkey.study.importer;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

//TODO move this to cloud module via the CloudStoreService
public class CloudStudyImporter
{
    public void process(StudyImportContext ctx, PipeRoot root, BindException errors) throws Exception
    {
        Logger log = ctx.getLoggerGetter().getLogger();
        if (!root.isCloudRoot())
        {
            log.debug("CloudStudyImport called with non-cloud root. Skipping study download.");
            return;
        }

        final CloudStoreService css = CloudStoreService.get();
        if (!ModuleLoader.getInstance().hasModule("Cloud") || css == null)
        {
            log.warn("CloudStoreService is not enabled");
            return;
        }

        //TODO Check if using a Zip file
        //Copy files locally to the import dir...
        StudyDocument.Study study = ctx.getXml();
        Path ctxRoot = root.resolveToNioPathFromUrl(ctx.getRoot().getLocation());
        final Path importRoot = root.getImportDirectory().toPath().toAbsolutePath();

        assert ctxRoot != null;
        downloadQueryFiles(study, ctxRoot, importRoot, log);
        downloadViewFiles(study, ctxRoot, importRoot, log);
        downloadReportsFiles(study, ctxRoot, importRoot, log);
        downloadListsFiles(study, ctxRoot, importRoot, log);
        downloadVisitsFiles(study, ctxRoot, importRoot, log);
        downloadCohortsFiles(study, ctxRoot, importRoot, log);
        downloadQCStateFiles(study, ctxRoot, importRoot, log);
        downloadDatasetsFiles(study, ctxRoot, importRoot, log);
        downloadSpecimensFiles(study, ctxRoot, importRoot, log);
    }

    private void downloadSpecimensFiles(StudyDocument.Study study, Path ctxRoot, Path importRoot, Logger log) throws IOException
    {
        StudyDocument.Study.Specimens specimens = study.getSpecimens();
        String specimensDir = specimens.getDir();
        String specimensFile = specimens.getFile();
        String specimensSettings = specimens.getSettings();

        Path dirSource = ctxRoot.resolve(specimensDir);
        Path fileSource = dirSource.resolve(specimensFile);
        Path settingsSource = dirSource.resolve(specimensSettings);
        copyFolder(dirSource, importRoot, log, StandardCopyOption.REPLACE_EXISTING);
        copyFolder(settingsSource, importRoot, log, StandardCopyOption.REPLACE_EXISTING);
        copyFolder(fileSource, importRoot, log, StandardCopyOption.REPLACE_EXISTING);
    }

    private void downloadQCStateFiles(StudyDocument.Study study, Path ctxRoot, Path importRoot, Logger log) throws IOException
    {
        String qcStatesDir = study.getQcStates().getFile();
        if (StringUtils.isNotBlank(qcStatesDir))
        {
            Path source = ctxRoot.resolve(qcStatesDir);
            copyFolder(source, importRoot, log, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void downloadDatasetsFiles(StudyDocument.Study study, Path ctxRoot, Path importRoot, Logger log) throws IOException
    {
        String datasetsDir = study.getDatasets().getDir();
        String datasetsFile = study.getDatasets().getFile();

        Path dirSource = ctxRoot.resolve(datasetsDir);
        Path fileSource = dirSource.resolve(datasetsFile);
        copyFolder(dirSource, importRoot, log, StandardCopyOption.REPLACE_EXISTING);
        copyFolder(fileSource, importRoot, log, StandardCopyOption.REPLACE_EXISTING);
    }

    private void downloadCohortsFiles(StudyDocument.Study study, Path ctxRoot, Path importRoot, Logger log) throws IOException
    {
        String cohortsFile = study.getCohorts().getFile();
        if (StringUtils.isNotBlank(cohortsFile))
        {
            Path source = ctxRoot.resolve(cohortsFile);
            copyFolder(source, importRoot, log, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void downloadListsFiles(StudyDocument.Study study, Path ctxRoot, Path importRoot, Logger log) throws IOException
    {
        String listsDir = study.getLists().getDir();
        Path source = ctxRoot.resolve(listsDir);
        copyFolder(source, importRoot, log, StandardCopyOption.REPLACE_EXISTING);
    }

    private void downloadVisitsFiles(StudyDocument.Study study, Path ctxRoot, Path importRoot, Logger log) throws IOException
    {
        String visitsDir = study.getVisits().getFile();
        Path source = ctxRoot.resolve(visitsDir);
        copyFolder(source, importRoot, log, StandardCopyOption.REPLACE_EXISTING);
    }

    private void downloadReportsFiles(StudyDocument.Study study, Path ctxRoot, Path importRoot, Logger log) throws IOException
    {
        String reportsDir = study.getReports().getDir();
        Path source = ctxRoot.resolve(reportsDir);
        copyFolder(source, importRoot, log, StandardCopyOption.REPLACE_EXISTING);
    }

    private void downloadViewFiles(StudyDocument.Study study, Path ctxRoot, Path importRoot, Logger log) throws IOException
    {
        String viewDir = study.getViews().getDir();
        Path source = ctxRoot.resolve(viewDir);
        copyFolder(source, importRoot, log, StandardCopyOption.REPLACE_EXISTING);
    }

    private void downloadQueryFiles(StudyDocument.Study study, Path ctxRoot, Path importRoot, Logger log) throws IOException
    {
        String queryDir = study.getQueries().getDir();
        Path source = ctxRoot.resolve(queryDir);
        copyFolder(source, importRoot, log, StandardCopyOption.REPLACE_EXISTING);
    }

    private void copyFolder(Path source, Path target, Logger log, CopyOption... options)
            throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                log.info("Creating dir " + dir);
                Files.createDirectories(target.resolve(source.getParent().relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                log.info("Downloading file " + file);
                Files.copy(file, target.resolve(source.getParent().relativize(file).toString()), options);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
