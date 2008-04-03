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
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Collection;

/**
 * User: jeckels
 * Date: Sep 20, 2007
 */
public interface ParticipantVisitResolverType
{
    public ParticipantVisitResolver createResolver(ExpRun run, Container targetStudyContainer, User user) throws IOException, ExperimentException;

    public ParticipantVisitResolver createResolver(Collection<ExpMaterial> inputMaterials,
                                                   Collection<ExpData> inputDatas,
                                                   Collection<ExpMaterial> outputMaterials,
                                                   Collection<ExpData> outputDatas,
                                                   Container runContainer,
                                                   Container targetStudyContainer, User user) throws IOException, ExperimentException;

    public String getName();

    public String getDescription();

    public void render(RenderContext ctx) throws Exception;

    public void addHiddenFormFields(InsertView view, AssayRunUploadForm form);

    public void configureRun(AssayRunUploadContext context, ExpRun run, Map<PropertyDescriptor, String> runProperties, Map<PropertyDescriptor, String> uploadSetProperties, Map<ExpData, String> inputDatas) throws ExperimentException;

    void putDefaultProperties(HttpServletRequest request, Map<String, String> properties);
}
