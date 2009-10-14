/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.controllers.samples;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Study;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.*;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.importer.SimpleSpecimenImporter;
import org.labkey.study.model.StudyManager;
import org.labkey.study.samples.settings.RepositorySettings;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.sql.SQLException;
import java.util.*;

@RequiresPermissionClass(AdminPermission.class)
public class ShowUploadSpecimensAction extends FormViewAction<ShowUploadSpecimensAction.UploadSpecimensForm> 
{
    public void validateCommand(UploadSpecimensForm form, Errors errors)
    {
        if (StringUtils.trimToNull(form.getTsv()) == null)
            errors.reject(SpringActionController.ERROR_MSG, "Please supply data to upload");
    }

    public ModelAndView getView(UploadSpecimensForm form, boolean reshow, BindException errors) throws Exception
    {
        RepositorySettings settings =  SampleManager.getInstance().getRepositorySettings(getViewContext().getContainer());
        if (!settings.isSimple())
            return HttpView.redirect(new ActionURL("Pipeline", "browse", getViewContext().getContainer()));

        return new JspView<UploadSpecimensForm>("/org/labkey/study/view/samples/uploadSimpleSpecimens.jsp", form, errors);
     }

    public boolean handlePost(UploadSpecimensForm form, BindException errors) throws Exception
    {
        SimpleSpecimenImporter importer = new SimpleSpecimenImporter();

        TabLoader loader = new TabLoader(form.getTsv(), true);
        Map<String,String> columnAliases = new CaseInsensitiveHashMap<String>();
        //Make sure we accept the labels
        for (Map.Entry<String,String> entry : importer.getColumnLabels().entrySet())
            columnAliases.put(entry.getValue(), entry.getKey());
        //And a few more aliases
        columnAliases.put("ParticipantId", SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put("Participant Id", SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put("Subject", SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put("SequenceNum", SimpleSpecimenImporter.VISIT);
        columnAliases.put("Sequence Num", SimpleSpecimenImporter.VISIT);
        columnAliases.put("Visit", SimpleSpecimenImporter.VISIT);
        columnAliases.put("specimenNumber", SimpleSpecimenImporter.SAMPLE_ID);
        columnAliases.put("specimen Number", SimpleSpecimenImporter.SAMPLE_ID);
        columnAliases.put("totalVolume", SimpleSpecimenImporter.VOLUME);
        columnAliases.put("total Volume", SimpleSpecimenImporter.VOLUME);
        columnAliases.put("volumeUnits", SimpleSpecimenImporter.UNITS);
        columnAliases.put("volume Units", SimpleSpecimenImporter.UNITS);
        columnAliases.put("primaryType", SimpleSpecimenImporter.PRIMARY_SPECIMEN_TYPE);
        columnAliases.put("primary Type", SimpleSpecimenImporter.PRIMARY_SPECIMEN_TYPE);
        columnAliases.put("additiveType", SimpleSpecimenImporter.ADDITIVE_TYPE);
        columnAliases.put("additive Type", SimpleSpecimenImporter.ADDITIVE_TYPE);
        columnAliases.put("derivativeType", SimpleSpecimenImporter.DERIVIATIVE_TYPE);
        columnAliases.put("derivative Type", SimpleSpecimenImporter.DERIVIATIVE_TYPE);
        columnAliases.put("Visit", SimpleSpecimenImporter.VISIT);
        columnAliases.put("drawTimestamp", SimpleSpecimenImporter.DRAW_TIMESTAMP);
        columnAliases.put("draw Timestamp", SimpleSpecimenImporter.DRAW_TIMESTAMP);
        columnAliases.put("globalUniqueId", SimpleSpecimenImporter.VIAL_ID);
        columnAliases.put("global Unique Id", SimpleSpecimenImporter.VIAL_ID);

        //Remember whether we used a different header so we can put up error messages that make sense
        Map<String,String> labels = new HashMap();
        for (ColumnDescriptor c : loader.getColumns())
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

        Set<String> participants = new HashSet<String>();
        Set<Object> vialIds = new HashSet<Object>();
        Study study = StudyManager.getInstance().getStudy(getViewContext().getContainer());
        Map<Object, Pair<Object,Object>> sampleIdMap = new HashMap<Object, Pair<Object, Object>>();
        String visitKey = study.isDateBased() ? SimpleSpecimenImporter.DRAW_TIMESTAMP : SimpleSpecimenImporter.VISIT;

        if (specimenRows.size() == 0)
            errors.reject(SpringActionController.ERROR_MSG, "No specimen data was provided.");
        int rowNum = 1;
        for (Map<String,Object> row : specimenRows)
        {
            String participant = (String) row.get(SimpleSpecimenImporter.PARTICIPANT_ID);
            if (null == participant)
                errors.reject(SpringActionController.ERROR_MSG, "Error, Row " + rowNum + " field " + (null == labels.get(SimpleSpecimenImporter.PARTICIPANT_ID) ? SimpleSpecimenImporter.PARTICIPANT_ID : labels.get(SimpleSpecimenImporter.PARTICIPANT_ID)) + " is not supplied");
            else
                participants.add((String) row.get(SimpleSpecimenImporter.PARTICIPANT_ID));

            Object sampleId = row.get(SimpleSpecimenImporter.SAMPLE_ID);
            if (null != sampleId)
            {
                Pair<Object,Object> participantVisit = new Pair<Object,Object>(participant, row.get(visitKey));
                if (sampleIdMap.containsKey(sampleId))
                {
                    if (!participantVisit.equals(sampleIdMap.get(sampleId)))
                        errors.reject(SpringActionController.ERROR_MSG, "Error, Row " + rowNum + " same sample id has multiple participant/visits.");
                }
                else
                    sampleIdMap.put(sampleId, participantVisit);
            }

            Object vialId = row.get(SimpleSpecimenImporter.VIAL_ID);
            if (null == vialId)
            {
                if (sampleId != null)
                {
                    vialId = sampleId;
                }
                else
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Error, Row " + rowNum + " missing " + (null == labels.get(SimpleSpecimenImporter.VIAL_ID) ? SimpleSpecimenImporter.VIAL_ID : labels.get(SimpleSpecimenImporter.VIAL_ID)));
                }
            }
            if (!vialIds.add(vialId))
                errors.reject(SpringActionController.ERROR_MSG, "Error, Row " + rowNum + " duplicate vial id " + vialId);

            Set<String> requiredFields = PageFlowUtil.set(SimpleSpecimenImporter.DRAW_TIMESTAMP);
            if (!study.isDateBased())
                requiredFields.add(SimpleSpecimenImporter.VISIT);
            for (String col : requiredFields)
            {
                if (null == row.get(col))
                    errors.reject(SpringActionController.ERROR_MSG, "Error, Row " + rowNum + " does not contain a value for field " + (null == labels.get(col) ? col : labels.get(col)));
            }

            if (errors.getAllErrors().size() >= 3)
                break;

            rowNum++;
        }

        try
        {
            if (!errors.hasErrors())
                importer.process(getViewContext().getUser(), study.getContainer(), specimenRows);
        }
        catch (SQLException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, "A database error was reported during import: " + e.getMessage());
        }
        return !errors.hasErrors();
    }

    public ActionURL getSuccessURL(UploadSpecimensForm form)
    {

        String redir = form.getRedir();
        if (null != StringUtils.trimToNull(redir))
            return new ActionURL(redir);
        else
            return new ActionURL(ImportCompleteAction.class, getViewContext().getContainer());
    }

    public NavTree appendNavTrail(NavTree root)
    {
        Container c = getViewContext().getContainer();
        Study s = StudyManager.getInstance().getStudy(c);

        root.addChild(s.getLabel(), new ActionURL(StudyController.OverviewAction.class, c));
        root.addChild("Specimens", new ActionURL(ShowSearchAction.class, c));
        root.addChild("Upload Specimens");

        return root;
    }

    @RequiresPermissionClass(AdminPermission.class)
    public static class ImportCompleteAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new HtmlView("Samples uploaded successfully.");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            Study study = StudyManager.getInstance().getStudy(getViewContext().getContainer());

            root.addChild(study.getLabel(), new ActionURL(StudyController.OverviewAction.class, getViewContext().getContainer()));
            root.addChild("Specimens", new ActionURL(ShowSearchAction.class, getViewContext().getContainer()));
            root.addChild("Sample Import Complete");

            return root;
        }
    }

    public static class UploadSpecimensForm
    {
        private String tsv;
        private String redir;

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
    }
}
