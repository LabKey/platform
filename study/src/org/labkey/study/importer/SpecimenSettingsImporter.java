package org.labkey.study.importer;

import org.labkey.api.admin.ImportException;
import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.SampleManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.xml.SpecimenRepositoryType;
import org.labkey.study.xml.SpecimenSettingsBaseType;
import org.labkey.study.xml.SpecimenSettingsType;
import org.labkey.study.xml.SpecimensDocument;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * User: kevink
 * Date: 6/13/13
 */
public class SpecimenSettingsImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "Specimen Settings Importer";
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile studyDir, BindException errors) throws SQLException, ImportException, IOException
    {
        StudyDocument.Study.Specimens xmlSettings = ctx.getXml().getSpecimens();
        if (xmlSettings != null)
        {
            ctx.getLogger().info("Loading specimen settings");

            StudyImpl study = StudyManager.getInstance().getStudy(ctx.getContainer()).createMutable();

            if (xmlSettings.getSettings() != null)
            {
                // Import specimen settings from specimen_settings.xml doc for versions >= 13.2.
                VirtualFile settingsDir = studyDir;
                if (xmlSettings.getDir() != null)
                    settingsDir = studyDir.getDir(xmlSettings.getDir());

                SpecimensDocument specimensDoc = (SpecimensDocument)settingsDir.getXmlBean(xmlSettings.getSettings());
                SpecimenSettingsType xmlSpecimens = specimensDoc.getSpecimens();

                importSettings(study, ctx, xmlSpecimens);
            }
            else
            {
                // Import specimen settings from study.xml doc for versions <13.2.
                importCommonSettings(study, ctx, xmlSettings);
            }

            StudyManager.getInstance().updateStudy(ctx.getUser(), study);
        }
    }

    // Import specimen settings for versions >= 13.2.
    private static void importSettings(StudyImpl study, StudyImportContext ctx, SpecimenSettingsType xmlSettings)
    {
        importCommonSettings(study, ctx, xmlSettings);

        // UNDONE: import other settings...
    }

    // Import specimen settings from study.xml doc for versions <13.2.
    private static void importCommonSettings(StudyImpl study, StudyImportContext ctx, SpecimenSettingsBaseType xmlSettings)
    {
        Container c = ctx.getContainer();
        RepositorySettings reposSettings = SampleManager.getInstance().getRepositorySettings(c);

        StudyDocument.Study.Specimens.SpecimenWebPartGroupings xmlSpecimenWebPartGroupSettings = xmlSettings.getSpecimenWebPartGroupings();
        if (null != xmlSpecimenWebPartGroupSettings)
        {
            StudyDocument.Study.Specimens.SpecimenWebPartGroupings.Grouping[] xmlGroupings = xmlSpecimenWebPartGroupSettings.getGroupingArray();
            if (null != xmlGroupings)
            {
                ArrayList<String[]> groupings = new ArrayList<>(2);
                for (int i = 0; i < xmlGroupings.length; i += 1)
                {
                    String[] groupBys = xmlGroupings[i].getGroupByArray();
                    groupings.add(groupBys);
                }
                reposSettings.setSpecimenWebPartGroupings(groupings);
            }
        }

        SpecimenRepositoryType.Enum repositoryType = xmlSettings.getRepositoryType();
        boolean simple = (SpecimenRepositoryType.STANDARD == repositoryType);
        reposSettings.setSimple(simple);
        reposSettings.setEnableRequests(!simple);
        SampleManager.getInstance().saveRepositorySettings(c, reposSettings);

        if (xmlSettings.isSetAllowReqLocRepository())
            study.setAllowReqLocRepository(xmlSettings.getAllowReqLocRepository());
        if (xmlSettings.isSetAllowReqLocClinic())
            study.setAllowReqLocClinic(xmlSettings.getAllowReqLocClinic());
        if (xmlSettings.isSetAllowReqLocSal())
            study.setAllowReqLocSal(xmlSettings.getAllowReqLocSal());
        if (xmlSettings.isSetAllowReqLocEndpoint())
            study.setAllowReqLocEndpoint(xmlSettings.getAllowReqLocEndpoint());
    }
}
