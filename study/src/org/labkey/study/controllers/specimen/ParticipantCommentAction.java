/*
 * Copyright (c) 2009-2015 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.DataView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UpdateView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.study.StudyService;
import org.labkey.study.controllers.InsertUpdateAction;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.Vial;
import org.labkey.study.model.SpecimenComment;
import org.labkey.study.SpecimenManager;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * User: klum
 * Date: Oct 7, 2009
 */
@RequiresPermission(ReadPermission.class)
public abstract class ParticipantCommentAction extends InsertUpdateAction<SpecimenController.ParticipantCommentForm>
{
    public ParticipantCommentAction()
    {
        super(SpecimenController.ParticipantCommentForm.class);
    }

    protected NavTree appendExtraNavTrail(NavTree root)
    {
        return null;
    }

    @Override
    public boolean handlePost(SpecimenController.ParticipantCommentForm form, BindException errors) throws Exception
    {
        if (super.handlePost(form, errors))
        {
            // clear any vial comments specified
            User user = getUser();
            Container container = getContainer();
            for (int rowId : form.getVialCommentsToClear())
            {
                Vial vial = SpecimenManager.getInstance().getVial(container, user, rowId);
                if (vial != null)
                {
                    SpecimenComment comment = SpecimenManager.getInstance().getSpecimenCommentForVial(vial);
                    if (comment != null)
                    {
                        SpecimenManager.getInstance().setSpecimenComment(user, vial, null,
                                comment.isQualityControlFlag(), comment.isQualityControlFlagForced());
                    }
                }
            }
            return true;
        }
        return false;
    }

    @RequiresPermission(ReadPermission.class)
    public static class SpecimenCommentInsertAction extends ParticipantCommentAction
    {
        private DataView _dataView;

        @Override
        public ModelAndView getView(final SpecimenController.ParticipantCommentForm form, boolean reshow, BindException errors) throws Exception
        {
            // super.getView() checks permissions
            ModelAndView view = super.getView(form, reshow, errors);
            if (!reshow && _dataView instanceof InsertView)
            {
                ((InsertView)_dataView).setInitialValue(StudyService.get().getSubjectColumnName(getContainer()), form.getParticipantId());
                ((InsertView)_dataView).setInitialValue("SequenceNum", form.getVisitId());

                if (!StringUtils.isBlank(form.getComment()))
                {
                    StudyImpl study = getStudy();
                    String commentProperty = form.getVisitId() != 0 ? study.getParticipantVisitCommentProperty() :
                            study.getParticipantCommentProperty();

                    ((InsertView)_dataView).setInitialValue(ColumnInfo.legalNameFromName(commentProperty), form.getComment());
                }
                for (int rowId : form.getVialCommentsToClear())
                    _dataView.getDataRegion().addHiddenFormField(SpecimenController.ParticipantCommentForm.params.vialCommentsToClear, String.valueOf(rowId));
            }
            return view;
        }

        @Override
        protected DataView createNewView(SpecimenController.ParticipantCommentForm form, QueryUpdateForm updateForm, BindException errors)
        {
            _dataView = super.createNewView(form, updateForm, errors);
            return _dataView;
        }

        protected boolean isInsert()
        {
            return true;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public static class SpecimenCommentUpdateAction extends ParticipantCommentAction
    {
        @Override
        protected DataView createNewView(final SpecimenController.ParticipantCommentForm form, QueryUpdateForm updateForm, BindException errors)
        {
            try
            {
                if (!StringUtils.isBlank(form.getComment()))
                {
                    updateForm.refreshFromDb();

                    StudyImpl study = getStudy();
                    String commentProperty = form.getVisitId() != 0 ? study.getParticipantVisitCommentProperty() :
                            study.getParticipantCommentProperty();
                    commentProperty = ColumnInfo.legalNameFromName(commentProperty);

                    Object values = updateForm.getOldValues();
                    if (values instanceof Map)
                    {
                        Map valueMap = (Map)values;
                        String oldComment = String.valueOf(valueMap.get(commentProperty));
                        String newComment = form.getComment();

                        if (!StringUtils.isBlank(oldComment) && !StringUtils.equals(oldComment, form.getComment()))
                            newComment = oldComment + " : " + form.getComment();
                        valueMap.put(commentProperty, newComment);
                    }
                }
                DataView view = new UpdateView(updateForm, errors);

                for (int rowId : form.getVialCommentsToClear())
                    view.getDataRegion().addHiddenFormField(SpecimenController.ParticipantCommentForm.params.vialCommentsToClear, String.valueOf(rowId));

                return view;
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        protected boolean isInsert()
        {
            return false;
        }
    }
}
