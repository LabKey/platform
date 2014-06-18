/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewForm;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Sep 20, 2007
 */
public class ThawListResolverType extends AssayFileWriter implements ParticipantVisitResolverType
{
    public static final String THAW_LIST_TYPE_INPUT_NAME = "ThawListType";
    public static final String THAW_LIST_TEXT_AREA_INPUT_NAME = "ThawListTextArea";
    static final String THAW_LIST_LIST_DEFINITION_INPUT_NAME = "ThawListListDefinition";

    public static final String NAMESPACE_PREFIX = "ThawList";
    public static final String LIST_NAMESPACE_SUFFIX = "List";
    public static final String TEXT_NAMESPACE_SUFFIX = "Text";
    public static final String THAW_LIST_LIST_CONTAINER_INPUT_NAME = "ThawListList-Container";
    public static final String THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME = "ThawListList-SchemaName";
    public static final String THAW_LIST_LIST_QUERY_NAME_INPUT_NAME = "ThawListList-QueryName";
    public static final Set<String> REQUIRED_COLUMNS = new CaseInsensitiveHashSet(Arrays.asList("Index", "SpecimenId", "ParticipantId", "VisitId", "Date"));

    public ParticipantVisitResolver createResolver(Collection<ExpMaterial> inputMaterials,
                                                   Collection<ExpData> inputDatas,
                                                   Collection<ExpMaterial> outputMaterials,
                                                   Collection<ExpData> outputDatas,
                                                   Container runContainer,
                                                   Container targetStudyContainer, User user) throws IOException, ExperimentException
    {
        ExpData thawListData = null;
        Lsid lsid = null;
        for (ExpData inputData : inputDatas)
        {
            lsid = new Lsid(inputData.getLSID());
            if (NAMESPACE_PREFIX.equals(lsid.getNamespacePrefix()))
            {
                thawListData = inputData;
                break;
            }
        }

        if (thawListData == null)
        {
            throw new ExperimentException("Could not find a thaw list for run");
        }

        ParticipantVisitResolver childResolver = new StudyParticipantVisitResolver(runContainer, targetStudyContainer, user);

        if (lsid.getNamespaceSuffix().startsWith(LIST_NAMESPACE_SUFFIX))
        {
            String objectId = lsid.getObjectId();
            int index = objectId.indexOf('.');
            if (index == -1)
            {
                throw new ExperimentException("Could not determine schema and query for data with LSID " + lsid);
            }
            String schemaName = objectId.substring(0, index);
            String queryName = objectId.substring(index + 1);
            Container listContainer = ContainerManager.getForPath(lsid.getVersion());
            if (listContainer == null)
            {
                throw new ExperimentException("Could not find container " + lsid.getVersion() + " for data with LSID " + lsid);
            }
            return new ThawListListResolver(runContainer, targetStudyContainer, listContainer, schemaName, queryName, user, childResolver);
        }
        else
        {
            File file = thawListData.getFile();
            if (file == null || !NetworkDrive.exists(file))
            {
                throw new ExperimentException("Could not find a thaw list for run");
            }

            Map<String, ParticipantVisit> values = new HashMap<>();
            TabLoader tabLoader = new TabLoader(file, true);

            for (Map<String, Object> data : tabLoader.load())
            {
                Object index = data.get("Index");
                Object specimenIDObject = data.get("SpecimenID");
                String specimenID = specimenIDObject == null ? null : specimenIDObject.toString();
                Object participantIDObject = data.get("ParticipantID");
                String participantID = participantIDObject == null ? null : participantIDObject.toString();
                Object visitIDObject = data.get("VisitID");
                if (visitIDObject != null && !(visitIDObject instanceof Number))
                {
                    throw new ExperimentException("The VisitID column in the thaw list must be a number.");
                }
                Double visitID = visitIDObject == null ? null : ((Number) visitIDObject).doubleValue();
                Object dateObject = data.get("Date");
                if (dateObject != null && !(dateObject instanceof Date))
                {
                    throw new ExperimentException("The Date column in the thaw list must be a date.");
                }
                Date date = (Date) dateObject;

                Container rowLevelTargetStudy = null;
                if (data.get("TargetStudy") != null)
                {
                    Set<Study> studies = StudyService.get().findStudy(data.get("TargetStudy"), null);
                    if (!studies.isEmpty())
                    {
                        Study study = studies.iterator().next();
                        rowLevelTargetStudy = study != null ? study.getContainer() : null;
                    }
                }

                Container targetStudy = rowLevelTargetStudy != null ? rowLevelTargetStudy : targetStudyContainer;
                values.put(index == null ? null : index.toString(), new ParticipantVisitImpl(specimenID, participantID, visitID, date, runContainer, targetStudy));
            }
            return new ThawListFileResolver(childResolver, values, runContainer);
        }
    }

    public ParticipantVisitResolver createResolver(ExpRun run, Container targetStudyContainer, User user) throws IOException, ExperimentException
    {
        return createResolver(run.getMaterialInputs().keySet(),
                run.getDataInputs().keySet(),
                run.getMaterialOutputs(),
                run.getDataOutputs(), run.getContainer(),
                targetStudyContainer, user);
    }

    public String getName()
    {
        return "Lookup";
    }

    public String getDescription()
    {
        return "Sample indices, which map to values in a different data source.";
    }

    public void render(RenderContext ctx) throws Exception
    {
        JspView<RenderContext> view = new JspView<>("/org/labkey/api/study/assay/thawListSelector.jsp", ctx);
        view.render(ctx.getRequest(), ctx.getViewContext().getResponse());

        // hack for 4404 : Lookup picker performance is terrible when there are many containers, so prime the cache
        ContainerManager.getAllChildren(ContainerManager.getRoot());
    }

    public void addHiddenFormFields(AssayRunUploadContext form, InsertView view)
    {
        view.getDataRegion().addHiddenFormField(THAW_LIST_TYPE_INPUT_NAME, form.getRequest().getParameter(THAW_LIST_TYPE_INPUT_NAME));
        view.getDataRegion().addHiddenFormField(THAW_LIST_LIST_DEFINITION_INPUT_NAME, form.getRequest().getParameter(THAW_LIST_LIST_DEFINITION_INPUT_NAME));
        view.getDataRegion().addHiddenFormField(THAW_LIST_TEXT_AREA_INPUT_NAME, form.getRequest().getParameter(THAW_LIST_TEXT_AREA_INPUT_NAME));
        view.getDataRegion().addHiddenFormField(THAW_LIST_LIST_CONTAINER_INPUT_NAME, form.getRequest().getParameter(THAW_LIST_LIST_CONTAINER_INPUT_NAME));
        view.getDataRegion().addHiddenFormField(THAW_LIST_LIST_QUERY_NAME_INPUT_NAME, form.getRequest().getParameter(THAW_LIST_LIST_QUERY_NAME_INPUT_NAME));
        view.getDataRegion().addHiddenFormField(THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME, form.getRequest().getParameter(THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME));
    }


    public void configureRun(AssayRunUploadContext context, ExpRun run, Map<ExpData, String> inputDatas) throws ExperimentException
    {
        String type = context.getRequest() == null ? null : context.getRequest().getParameter(THAW_LIST_TYPE_INPUT_NAME);

        InputStream in = null;

        ExpData thawListData = ExperimentService.get().createData(context.getContainer(), new DataType(NAMESPACE_PREFIX));
        String name;
        String dataLSID;

        if (TEXT_NAMESPACE_SUFFIX.equals(type))
        {
            try
            {
                String text = context.getRequest().getParameter(THAW_LIST_TEXT_AREA_INPUT_NAME);
                if (text != null)
                {
                    in = new ByteArrayInputStream(text.getBytes());
                }

                File uploadDir = ensureUploadDirectory(context.getContainer());
                File file = createFile(context.getProtocol(), uploadDir, "thawList");
                try
                {
                    writeFile(in, file);
                }
                catch (IOException e)
                {
                    throw new ExperimentException(e);
                }
                name = file.getName();
                dataLSID = new Lsid(NAMESPACE_PREFIX, TEXT_NAMESPACE_SUFFIX + ".Folder-" + context.getContainer().getRowId(),
                        name).toString();
                thawListData.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(file).toURI());
            }
            finally
            {
                if (in != null) { try { in.close(); } catch (IOException ignored) {} }
            }
        }
        else if (LIST_NAMESPACE_SUFFIX.equals(type))
        {
            String containerName = context.getRequest().getParameter(THAW_LIST_LIST_CONTAINER_INPUT_NAME);
            String schemaName = context.getRequest().getParameter(THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME);
            String queryName = context.getRequest().getParameter(THAW_LIST_LIST_QUERY_NAME_INPUT_NAME);
            Container container;
            if (containerName == null || "".equals(containerName))
            {
                container = context.getContainer();
            }
            else
            {
                container = ContainerManager.getForPath(containerName);
            }

            String validationErrMsg = validateThawList(context.getRequest(), container, context.getUser());
            if (StringUtils.isNotEmpty(validationErrMsg))
            {
                throw new ExperimentException(validationErrMsg);
            }

            name = makeThawListName(schemaName, queryName, container);
            Lsid lsid = new Lsid(NAMESPACE_PREFIX, LIST_NAMESPACE_SUFFIX + ".Folder-" + context.getContainer().getRowId(),
                    schemaName + "." + queryName);
            lsid.setVersion(container.getPath());
            dataLSID = lsid.toString();

            ExpData existingData = ExperimentService.get().getExpData(dataLSID);
            if (existingData != null)
            {
                thawListData = existingData;
            }
        }
        else
        {
            throw new IllegalArgumentException("Unsupported thaw list type: " + type);
        }

        thawListData.setName(name);
        thawListData.setLSID(dataLSID);
        inputDatas.put(thawListData, "ThawList");
    }

    /**
     * Determine if the submitted form is using a thaw list, and if so, validate the list existence, permissions,
     * and columns.
     * @param form
     * @param errors
     */
    public static void validationHelper(ViewForm form, Errors errors)
    {
        if (LIST_NAMESPACE_SUFFIX.equalsIgnoreCase(form.getRequest().getParameter(THAW_LIST_TYPE_INPUT_NAME)))
        {
            String errMsg = validateThawList(form.getRequest(), form.getContainer(), form.getUser());
            if (StringUtils.isNotEmpty(errMsg))
            {
                errors.reject(SpringActionController.ERROR_MSG, errMsg);
            }
        }
    }

    /**
     * Validate the existence, permissions, and columns of a thaw list.
     * @param request
     * @param c
     * @param u
     * @return
     */
    private static String validateThawList(HttpServletRequest request, Container c, User u)
    {
        String containerName = request.getParameter(THAW_LIST_LIST_CONTAINER_INPUT_NAME);
        String schemaName = request.getParameter(THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME);
        String queryName = request.getParameter(THAW_LIST_LIST_QUERY_NAME_INPUT_NAME);
        Container container;

        if (containerName == null || "".equals(containerName))
        {
            container = c;
        }
        else
        {
            container = ContainerManager.getForPath(containerName);
        }

        if (container == null || !container.hasPermission(u, ReadPermission.class))
        {
            return("Could not reference container " + containerName);
        }

        UserSchema schema = QueryService.get().getUserSchema(u, container, schemaName);
        if (schema == null)
        {
            return("Could not find schema " + schemaName);
        }
        TableInfo table = schema.getTable(queryName);
        if (table == null)
        {
            return("Could not find table " + queryName);
        }

        StringBuilder sb = new StringBuilder();
        for (String column : REQUIRED_COLUMNS)
        {
            if (table.getColumn(FieldKey.fromParts(column)) == null)
            {
                if (sb.length() > 0)
                    sb.append("\n");
                else
                    sb.append(makeThawListName(schemaName, queryName, container) + " is missing required column(s):\n");
                sb.append(column);
            }
        }

        return sb.toString();
    }

    private static String makeThawListName(String schemaName, String queryName, Container container)
    {
        return schemaName + "." + queryName + " in " + container.getPath();
    }

    public boolean collectPropertyOnUpload(AssayRunUploadContext uploadContext, String propertyName)
    {
        return !(propertyName.equals(AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME) ||
                propertyName.equals(AbstractAssayProvider.VISITID_PROPERTY_NAME) ||
                propertyName.equals(AbstractAssayProvider.DATE_PROPERTY_NAME));
    }
}
