package org.labkey.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.xml.SpecimenRepositoryType;
import org.labkey.study.xml.SpecimenSettingsType;
import org.labkey.study.xml.SpecimensDocument;
import org.labkey.study.xml.StudyDocument;

import java.util.ArrayList;

/**
 * User: kevink
 * Date: 6/13/13
 */
public class SpecimenSettingsWriter extends AbstractSpecimenWriter
{
    private static final String DEFAULT_SETTINGS_FILE = "specimen_settings.xml";

    @Nullable
    @Override
    public String getSelectionText()
    {
        return "Specimen Settings";
    }

    @Override
    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile root) throws Exception
    {
        StudyDocument.Study.Specimens specimensXml = ensureSpecimensElement(ctx);
        specimensXml.setSettings(DEFAULT_SETTINGS_FILE);

        VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);
        writeSettings(study, ctx, vf);
    }

    private void writeSettings(StudyImpl study, StudyExportContext ctx, VirtualFile dir) throws Exception
    {
        SpecimensDocument settingsDoc = SpecimensDocument.Factory.newInstance();
        SpecimenSettingsType settingsXml = settingsDoc.addNewSpecimens();

        RepositorySettings repositorySettings = study.getRepositorySettings();
        settingsXml.setRepositoryType(repositorySettings.isSimple() ? SpecimenRepositoryType.STANDARD : SpecimenRepositoryType.ADVANCED);
        settingsXml.setAllowReqLocRepository(study.isAllowReqLocRepository());
        settingsXml.setAllowReqLocClinic(study.isAllowReqLocClinic());
        settingsXml.setAllowReqLocSal(study.isAllowReqLocSal());
        settingsXml.setAllowReqLocEndpoint(study.isAllowReqLocEndpoint());
        ArrayList<String[]> groupings = repositorySettings.getSpecimenWebPartGroupings();
        if (groupings.size() > 0)
        {
            SpecimenSettingsType.SpecimenWebPartGroupings specimenWebPartGroupings = settingsXml.addNewSpecimenWebPartGroupings();
            for (String[] grouping : groupings)
            {
                SpecimenSettingsType.SpecimenWebPartGroupings.Grouping specimenWebPartGrouping = specimenWebPartGroupings.addNewGrouping();
                specimenWebPartGrouping.setGroupByArray(grouping);
            }
        }
        dir.saveXmlBean(DEFAULT_SETTINGS_FILE, settingsDoc);
    }

}
