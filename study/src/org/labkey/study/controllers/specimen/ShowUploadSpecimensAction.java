/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
package org.labkey.study.controllers.specimen;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.*;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.study.SpecimenManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.importer.SimpleSpecimenImporter;
import org.labkey.study.model.StudyManager;
import org.labkey.study.specimen.settings.RepositorySettings;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.*;

@RequiresPermission(AdminPermission.class)
public class ShowUploadSpecimensAction extends FormViewAction<ShowUploadSpecimensAction.UploadSpecimensForm> 
{
    public void validateCommand(UploadSpecimensForm form, Errors errors)
    {
        if (StringUtils.trimToNull(form.getTsv()) == null)
            errors.reject(SpringActionController.ERROR_MSG, "Please supply data to upload");
    }

    public ModelAndView getView(UploadSpecimensForm form, boolean reshow, BindException errors) throws Exception
    {
        Container container = getContainer();
        RepositorySettings settings =  SpecimenManager.getInstance().getRepositorySettings(container);
        if (!settings.isSimple())
            return HttpView.redirect(PageFlowUtil. urlProvider(PipelineUrls.class).urlBrowse(container));

        boolean isEmpty = SpecimenManager.getInstance().isSpecimensEmpty(container, getUser());
        if (isEmpty)
        {
            form.setNoSpecimens(true);
            form.setReplaceOrMerge("replace");
        }
        return new JspView<>("/org/labkey/study/view/specimen/uploadSimpleSpecimens.jsp", form, errors);
     }

    public boolean handlePost(UploadSpecimensForm form, BindException errors) throws Exception
    {
        Container container = getContainer();
        User user = getUser();
        Study study = StudyManager.getInstance().getStudy(container);
        SimpleSpecimenImporter importer = new SimpleSpecimenImporter(container, user,
                study != null ? study.getTimepointType() : TimepointType.DATE,
                StudyService.get().getSubjectNounSingular(container));

        TabLoader loader = new TabLoader(form.getTsv(), true);
        Map<String, String> columnAliases = new CaseInsensitiveHashMap<>();
        //Make sure we accept the labels
        for (Map.Entry<String, String> entry : importer.getColumnLabels().entrySet())
            columnAliases.put(entry.getValue(), entry.getKey());
        //And a few more aliases
        columnAliases.put(StudyService.get().getSubjectColumnName(container), SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put(StudyService.get().getSubjectNounSingular(container), SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put("ParticipantId", SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put("Participant Id", SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put("Participant", SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put("Subject", SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put("SequenceNum", SimpleSpecimenImporter.VISIT);
        columnAliases.put("Sequence Num", SimpleSpecimenImporter.VISIT);
        columnAliases.put("Visit", SimpleSpecimenImporter.VISIT);
        columnAliases.put("Timepoint Number", SimpleSpecimenImporter.VISIT);
        columnAliases.put("specimenNumber", SimpleSpecimenImporter.SAMPLE_ID);
        columnAliases.put("specimen Number", SimpleSpecimenImporter.SAMPLE_ID);
        columnAliases.put("totalVolume", SimpleSpecimenImporter.VOLUME);
        columnAliases.put("total Volume", SimpleSpecimenImporter.VOLUME);
        columnAliases.put("volumeUnits", SimpleSpecimenImporter.UNITS);
        columnAliases.put("volume Units", SimpleSpecimenImporter.UNITS);
        columnAliases.put("primaryType", SimpleSpecimenImporter.PRIMARY_SPECIMEN_TYPE);
        columnAliases.put("primary Type", SimpleSpecimenImporter.PRIMARY_SPECIMEN_TYPE);
        columnAliases.put("additive", SimpleSpecimenImporter.ADDITIVE_TYPE);
        columnAliases.put("additiveType", SimpleSpecimenImporter.ADDITIVE_TYPE);
        columnAliases.put("additive Type", SimpleSpecimenImporter.ADDITIVE_TYPE);
        columnAliases.put("derivative", SimpleSpecimenImporter.DERIVIATIVE_TYPE);
        columnAliases.put("derivativeType", SimpleSpecimenImporter.DERIVIATIVE_TYPE);
        columnAliases.put("derivative Type", SimpleSpecimenImporter.DERIVIATIVE_TYPE);
        columnAliases.put("drawTimestamp", SimpleSpecimenImporter.DRAW_TIMESTAMP);
        columnAliases.put("Draw Date", SimpleSpecimenImporter.DRAW_TIMESTAMP);
        columnAliases.put("Date", SimpleSpecimenImporter.DRAW_TIMESTAMP);
        columnAliases.put("draw Timestamp", SimpleSpecimenImporter.DRAW_TIMESTAMP);
        columnAliases.put("Vial Id", SimpleSpecimenImporter.VIAL_ID);
        columnAliases.put("globalUniqueId", SimpleSpecimenImporter.VIAL_ID);
        columnAliases.put("global Unique Id", SimpleSpecimenImporter.VIAL_ID);
        columnAliases.put("Units", SimpleSpecimenImporter.UNITS);
        columnAliases.put("Specimen Type", SimpleSpecimenImporter.PRIMARY_SPECIMEN_TYPE);

        //Remember whether we used a different header so we can put up error messages that make sense
        Map<String, String> labels = new HashMap<>();
        ColumnDescriptor[] columns;
        try
        {
            columns = loader.getColumns();
        }
        catch (IOException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            return false;
        }

        for (ColumnDescriptor c : columns)
        {
            if (columnAliases.containsKey(c.name))
            {
                labels.put(columnAliases.get(c.name), c.name);
                c.name = columnAliases.get(c.name);
            }
            else
                labels.put(c.name, c.name);
        }
        importer.fixupSpecimenColumns(loader);
        List<Map<String,Object>> specimenRows;
        try
        {
            loader.setThrowOnErrors(true);
            specimenRows = loader.load();
        }
        catch (ConversionException x)
        {
            errors.reject(SpringActionController.ERROR_MSG, x.getMessage() + " NOTE: Numbers must contain only digits and decimal separators.");
            return false;
        }

        Set<String> participants = new HashSet<>();
        Set<Object> vialIds = new HashSet<>();
        Map<Object, Pair<Object,Object>> sampleIdMap = new HashMap<>();
        String visitKey = study.getTimepointType() == TimepointType.VISIT ? SimpleSpecimenImporter.VISIT : SimpleSpecimenImporter.DRAW_TIMESTAMP;

        if (specimenRows.size() == 0)
            errors.reject(SpringActionController.ERROR_MSG, "No specimen data was provided.");
        int rowNum = 1;
        for (Map<String,Object> row : specimenRows)
        {
            Object vialId = row.get(SimpleSpecimenImporter.VIAL_ID);

            String participant = (String) row.get(SimpleSpecimenImporter.PARTICIPANT_ID);
            if (null == participant && null != vialId)
            {
                participant = (String) vialId;
                row.put(SimpleSpecimenImporter.PARTICIPANT_ID, participant);
            }
            if (null == participant)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Error in row " + rowNum + ": required field " + (null == labels.get(SimpleSpecimenImporter.PARTICIPANT_ID) ?
                        StudyService.get().getSubjectNounSingular(container) :
                        labels.get(SimpleSpecimenImporter.PARTICIPANT_ID)) + " was not supplied");
            }
            else
                participants.add(participant);

            Object sampleId = row.get(SimpleSpecimenImporter.SAMPLE_ID);
            if (null == sampleId && null != vialId)
            {
                sampleId = row.get(SimpleSpecimenImporter.VIAL_ID);
                row.put(SimpleSpecimenImporter.SAMPLE_ID, sampleId);
            }
            if (null != sampleId)
            {
                Pair<Object,Object> participantVisit = new Pair<Object,Object>(participant, row.get(visitKey));
                if (sampleIdMap.containsKey(sampleId))
                {
                    if (!participantVisit.equals(sampleIdMap.get(sampleId)))
                        errors.reject(SpringActionController.ERROR_MSG, "Error in row " + rowNum + ": the same sample id has multiple " +
                                StudyService.get().getSubjectNounSingular(container) + "/visits.");
                }
                else
                    sampleIdMap.put(sampleId, participantVisit);
            }

            if (null == vialId)
            {
                if (sampleId != null)
                {
                    vialId = sampleId;
                }
                else
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Error in row " + rowNum + ": missing " + (null == labels.get(SimpleSpecimenImporter.VIAL_ID) ? SimpleSpecimenImporter.VIAL_ID : labels.get(SimpleSpecimenImporter.VIAL_ID)));
                }
            }
            if (!vialIds.add(vialId))
                errors.reject(SpringActionController.ERROR_MSG, "Error in row " + rowNum + ": duplicate vial id " + vialId);

            Set<String> requiredFields;
            if (study.getTimepointType() == TimepointType.DATE)
                requiredFields = PageFlowUtil.set(SimpleSpecimenImporter.DRAW_TIMESTAMP);
            else
                requiredFields = PageFlowUtil.set(SimpleSpecimenImporter.VISIT);
            for (String col : requiredFields)
            {
                if (null == row.get(col))
                    errors.reject(SpringActionController.ERROR_MSG, "Error: row " + rowNum + " does not contain a value for field " + (null == labels.get(col) ? col : labels.get(col)));
            }

            if (errors.getAllErrors().size() >= 3)
                break;

            rowNum++;
        }

        try
        {
            if (!errors.hasErrors())
                importer.process(specimenRows, form.isMerge());
        }
        catch (IllegalStateException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
        }
        catch (RuntimeSQLException e)
        {
            String message = e.getMessage();
            if (e.getSQLException() instanceof BatchUpdateException && null != e.getSQLException().getNextException())
                message = e.getSQLException().getNextException().getMessage();
            errors.reject(SpringActionController.ERROR_MSG, "A database error was reported during import: " + message);
        }
        catch (RuntimeException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
        }
        return !errors.hasErrors();
    }

    public ActionURL getSuccessURL(UploadSpecimensForm form)
    {

        String redir = form.getRedir();
        if (null != StringUtils.trimToNull(redir))
            return new ActionURL(redir);
        else
            return new ActionURL(ImportCompleteAction.class, getContainer());
    }

    public NavTree appendNavTrail(NavTree root)
    {
        Container c = getContainer();
        Study s = StudyManager.getInstance().getStudy(c);

        root.addChild(s.getLabel(), new ActionURL(StudyController.OverviewAction.class, c));
        root.addChild("Specimen Overview", new ActionURL(SpecimenController.OverviewAction.class, c));
        root.addChild("Upload Specimens");

        return root;
    }

    @RequiresPermission(AdminPermission.class)
    public static class ImportCompleteAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ActionURL homeLink = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(getContainer());
            ActionURL samplesLink = new ActionURL(SpecimenController.BeginAction.class, getContainer());
            samplesLink.addParameter(SpecimenController.SampleViewTypeForm.PARAMS.showVials, Boolean.TRUE.toString());
            return new HtmlView("Specimens uploaded successfully.<br><br>" +
                    PageFlowUtil.textLink("study home", homeLink) + " " +
                    PageFlowUtil.textLink("specimens", samplesLink));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            Study study = StudyManager.getInstance().getStudy(getContainer());

            root.addChild(study.getLabel(), new ActionURL(StudyController.OverviewAction.class, getContainer()));
            root.addChild("Specimen Overview", new ActionURL(SpecimenController.OverviewAction.class, getContainer()));
            root.addChild("Sample Import Complete");

            return root;
        }
    }

    public static class UploadSpecimensForm
    {
        private String tsv;
        private String redir;
        private String replaceOrMerge = "merge";
        private boolean noSpecimens = false;

        public String getTsv()
        {
            return tsv;
        }

        public void setTsv(String tsv)
        {
            this.tsv = tsv;
        }

        public String getRedir()
        {
            return redir;
        }

        public void setRedir(String redir)
        {
            this.redir = redir;
        }

        public String getReplaceOrMerge()
        {
            return replaceOrMerge;
        }

        public void setReplaceOrMerge(String replaceOrMerge)
        {
            this.replaceOrMerge = replaceOrMerge;
        }

        public boolean isMerge()
        {
            return "merge".equals(this.replaceOrMerge);
        }

        public boolean isNoSpecimens()
        {
            return noSpecimens;
        }

        public void setNoSpecimens(boolean noSpecimens)
        {
            this.noSpecimens = noSpecimens;
        }
    }
}
