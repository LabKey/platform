package org.labkey.api.study.assay;

import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.view.InsertView;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.security.User;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Collection;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Sep 20, 2007
 */
public class StudyParticipantVisitResolverType implements ParticipantVisitResolverType
{
    public ParticipantVisitResolver createResolver(ExpRun run, Container targetStudyContainer, User user)
    {
        return new StudyParticipantVisitResolver(run.getContainer(), targetStudyContainer);
    }


    public ParticipantVisitResolver createResolver(Collection<ExpMaterial> inputMaterials,
                                                   Collection<ExpData> inputDatas,
                                                   Collection<ExpMaterial> outputMaterials,
                                                   Collection<ExpData> outputDatas,
                                                   Container runContainer,
                                                   Container targetStudyContainer, User user) throws IOException, ExperimentException
    {
        return new StudyParticipantVisitResolver(runContainer, targetStudyContainer);
    }

    public String getName()
    {
        return "SampleInfo";
    }

    public String getDescription()
    {
        return "I will supply sample information, or I don't plan to map to participant and visit information.";
    }

    public void render(RenderContext ctx)
    {
    }

    public void addHiddenFormFields(InsertView view, AssayRunUploadForm form)
    {
        // Don't need to add any form fields - the data's already all there
    }

    public void configureRun(AssayRunUploadContext context, ExpRun run, Map<PropertyDescriptor, String> runProperties, Map<PropertyDescriptor, String> uploadSetProperties, Map<ExpData, String> inputDatas)
    {
        // Don't need to do anything - the data's already all there
    }

    public void putDefaultProperties(HttpServletRequest request, Map<String, String> properties)
    {
        // No form fields, so we don't need to add anything
    }
}
