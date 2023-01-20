package org.labkey.study.importer;

import org.apache.xmlbeans.XmlObject;
import org.labkey.api.admin.ImportException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.security.permissions.StudyPermissionExporter;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.xml.StudyDocument;
import org.labkey.studySecurityPolicy.xml.StudySecurityPolicyDocument;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.util.List;

public class StudySecurityPolicyImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return getDataType();
    }

    @Override
    public String getDataType() { return StudyArchiveDataTypes.STUDY_SECURITY_POLICY; }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws IOException, ValidationException, ImportException
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (isValidForImportArchive(ctx, root))
        {
            ctx.getLogger().info("Loading " + getDescription());

            StudyDocument.Study.StudySecurity studySecurity = ctx.getXml().getStudySecurity();
            String policyFileName = studySecurity.getFile();

            if (policyFileName != null)
            {
                ctx.getLogger().info("Loading security policy file from " + policyFileName);
                try
                {
                    XmlObject doc = root.getXmlBean(policyFileName);
                    if (doc instanceof StudySecurityPolicyDocument spd)
                    {
                        XmlBeansUtil.validateXmlDocument(doc, "security policy file");
                        StudyPermissionExporter exporter = new StudyPermissionExporter();
                        List<String> errorMsg = exporter.loadSecurityPolicyDocument(ctx.getStudyImpl(), ctx.getUser(), spd);
                        errorMsg.forEach(errors::reject);
                    }
                }
                catch (XmlValidationException e)
                {
                    throw new ImportException("Invalid study security policy document");
                }
            }
            else
                ctx.getLogger().error("Security policy file is not set");

            ctx.getLogger().info("Done importing " + getDescription());
        }
    }

    @Override
    public boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        return ctx.getXml() != null && ctx.getXml().getStudySecurity() != null;
    }
}
