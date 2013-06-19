package org.labkey.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.SampleRequestActor;
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
        SpecimensDocument xmlSettingsDoc = SpecimensDocument.Factory.newInstance();
        SpecimenSettingsType xmlSettings = xmlSettingsDoc.addNewSpecimens();

        RepositorySettings repositorySettings = study.getRepositorySettings();
        xmlSettings.setRepositoryType(repositorySettings.isSimple() ? SpecimenRepositoryType.STANDARD : SpecimenRepositoryType.ADVANCED);

        // specimen location types
        SpecimenSettingsType.LocationTypes xmlLocationTypes = xmlSettings.addNewLocationTypes();
        xmlLocationTypes.addNewRepository().setAllowRequests(study.isAllowReqLocRepository());
        xmlLocationTypes.addNewClinic().setAllowRequests(study.isAllowReqLocClinic());
        xmlLocationTypes.addNewSiteAffiliatedLab().setAllowRequests(study.isAllowReqLocSal());
        xmlLocationTypes.addNewEndpointLab().setAllowRequests(study.isAllowReqLocEndpoint());

        // specimen webpart groupings
        ArrayList<String[]> groupings = repositorySettings.getSpecimenWebPartGroupings();
        if (groupings.size() > 0)
        {
            SpecimenSettingsType.WebPartGroupings xmlWebPartGroupings = xmlSettings.addNewWebPartGroupings();
            for (String[] grouping : groupings)
            {
                SpecimenSettingsType.WebPartGroupings.Grouping xmlGrouping = xmlWebPartGroupings.addNewGrouping();
                xmlGrouping.setGroupByArray(grouping);
            }
        }

        // UNDONE: request statuses

        // UNDONE: request actors

        // UNDONE: default requirements

        // write out the xml
        dir.saveXmlBean(DEFAULT_SETTINGS_FILE, xmlSettingsDoc);
    }

}
