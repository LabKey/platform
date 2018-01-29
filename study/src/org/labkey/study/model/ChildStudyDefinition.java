/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.study.model;

import org.apache.log4j.Logger;
import org.labkey.api.data.PHI;
import org.labkey.api.study.StudySnapshotType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: klum
 * Date: Aug 25, 2011
 * Time: 3:51:31 PM
 */
public class ChildStudyDefinition
{
    private StudySnapshotType _mode;
    private String _name;
    private String _description;
    private String _srcPath;
    private String _dstPath;
    private PHI _exportPhiLevel;
    private boolean _shiftDates;
    private boolean _useAlternateParticipantIds;
    private boolean _maskClinic;
    private boolean _includeSpecimens;
    private boolean _specimenRefresh = false;
    private Integer[] _visits;
    private Integer[] _lists;
    private String[] _views;
    private String[] _reports;
    private int[] _datasets = new int[0];
    private boolean update;
    private int _updateDelay;
    private String[] _studyProps;
    private String[] _folderProps;

    private int[] _groups = new int[0];
    private boolean _copyParticipantGroups;

    private Integer _requestId;  // RowId of a specimen request
    private String[] _specimenIds = null;  // List of globally unique specimen IDs
    private List<Vial> _vials = null;

    // used to persist the snapshot settings (i.e. all vs selected subset)
    private boolean _participantGroupsAll;
    private boolean _studyPropsAll;
    private boolean _folderPropsAll;
    private boolean _viewsAll;
    private boolean _reportsAll;

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getSrcPath()
    {
        return _srcPath;
    }

    public void setSrcPath(String srcPath)
    {
        _srcPath = srcPath;
    }

    public String getDstPath()
    {
        return _dstPath;
    }

    public void setDstPath(String dstPath)
    {
        _dstPath = dstPath;
    }

    public int[] getDatasets()
    {
        return _datasets;
    }

    public void setDatasets(int[] datasets)
    {
        _datasets = datasets;
    }

    public void setStudyProps(String[] props)
    {
        _studyProps = props;
    }

    public String[] getStudyProps()
    {
        return _studyProps;
    }

    public void setFolderProps(String[] props)
    {
        _folderProps = props;
    }

    public String[] getFolderProps()
    {
        return _folderProps;
    }

    public int[] getGroups()
    {
        return _groups;
    }

    public void setGroups(int[] groups)
    {
        _groups = groups;
    }

    public boolean isUpdate()
    {
        return update;
    }

    public void setUpdate(boolean update)
    {
        this.update = update;
    }

    public int getUpdateDelay()
    {
        return _updateDelay;
    }

    public void setUpdateDelay(int updateDelay)
    {
        _updateDelay = updateDelay;
    }

    public boolean isCopyParticipantGroups()
    {
        return _copyParticipantGroups;
    }

    public void setCopyParticipantGroups(boolean copyParticipantGroups)
    {
        _copyParticipantGroups = copyParticipantGroups;
    }

    public PHI getExportPhiLevel(){
        return null != _exportPhiLevel ? _exportPhiLevel : PHI.NotPHI;
    }

    public void setExportPhiLevel(PHI exportPhiLevel){
        _exportPhiLevel = exportPhiLevel;
    }

    public boolean isShiftDates(){
        return _shiftDates;
    }

    public void setShiftDates(boolean shiftDates){
        _shiftDates = shiftDates;
    }

    public boolean isUseAlternateParticipantIds(){
        return _useAlternateParticipantIds;
    }

    public void setUseAlternateParticipantIds(boolean useAlternateParticipantIds){
        _useAlternateParticipantIds = useAlternateParticipantIds;
    }

    public boolean isMaskClinic(){
        return _maskClinic;
    }

    public void setMaskClinic(boolean maskClinic){
        _maskClinic = maskClinic;
    }

    public Integer[] getVisits(){
        return _visits;
    }

    public void setVisits(Integer[] visits){
        _visits = visits;
    }

    public Integer[] getLists(){
        return _lists;
    }

    public void setLists(Integer[] lists){
        _lists = lists;
    }

    public String[] getReports(){
        return _reports;
    }

    public void setReports(String[] views){
        _reports = views;
    }

    public String[] getViews(){
        return _views;
    }

    public void setViews(String[] views){
        _views = views;
    }

    public boolean isIncludeSpecimens()
    {
        return _includeSpecimens;
    }

    public void setIncludeSpecimens(boolean includeSpecimens)
    {
        _includeSpecimens = includeSpecimens;
    }

    public boolean isSpecimenRefresh()
    {
        return _specimenRefresh;
    }

    public void setSpecimenRefresh(boolean specimenRefresh)
    {
        _specimenRefresh = specimenRefresh;
    }

    // Callers can post either a specimen request id OR an array of SpecimenIds; either will cause us to resovle
    // to a Specimen[] which is set back on the form and used to drive the child study creation process.

    public Integer getRequestId()
    {
        return _requestId;
    }

    public void setRequestId(Integer requestId)
    {
        _requestId = requestId;
    }

    public String[] getSpecimenIds()
    {
        return _specimenIds;
    }

    public void setSpecimenIds(String[] specimenIds)
    {
        _specimenIds = specimenIds;
    }

    public List<Vial> getVials()
    {
        return _vials;
    }

    public void setVials(List<Vial> vials)
    {
        _vials = vials;
    }

    public StudySnapshotType getMode()
    {
        return _mode;
    }

    public void setMode(StudySnapshotType mode)
    {
        _mode = mode;
    }

    public boolean isStudyPropsAll()
    {
        return _studyPropsAll;
    }

    public void setStudyPropsAll(boolean studyPropsAll)
    {
        _studyPropsAll = studyPropsAll;
    }

    public boolean isFolderPropsAll()
    {
        return _folderPropsAll;
    }

    public void setFolderPropsAll(boolean folderPropsAll)
    {
        _folderPropsAll = folderPropsAll;
    }

    public boolean isViewsAll()
    {
        return _viewsAll;
    }

    public void setViewsAll(boolean viewsAll)
    {
        _viewsAll = viewsAll;
    }

    public boolean isReportsAll()
    {
        return _reportsAll;
    }

    public void setReportsAll(boolean reportsAll)
    {
        _reportsAll = reportsAll;
    }

    public boolean isParticipantGroupsAll()
    {
        return _participantGroupsAll;
    }

    public void setParticipantGroupsAll(boolean participantGroupsAll)
    {
        _participantGroupsAll = participantGroupsAll;
    }

    public void logSelections(Logger logger)
    {
        logger.info("----- Start Selected Properties -----");
        logger.info("Name: " + getName());
        logger.info("Source Path: " + getSrcPath());
        logger.info("Destination Path: " + getDstPath());
        logger.info("Participant Groups: " + (isParticipantGroupsAll() ? "All" : Arrays.toString(getGroups())));
        logger.info("Datasets: " + Arrays.toString(getDatasets()));
        logger.info("Data Refresh: " + (isUpdate() ? (getUpdateDelay() > 0 ? "Automatic" : "Manual") : "None"));
        logger.info("Visits: " + arrayToString(getVisits()));
        logger.info("Specimens: " + (isIncludeSpecimens() ? (isSpecimenRefresh() ? "Nightly refresh" : "One-time snapshot") : "None"));
        logger.info("Study Objects: " + arrayToString(getStudyProps()));
        logger.info("Folder Objects: " + arrayToString(getFolderProps()));
        logger.info("Publish Options: " + arrayToString(getPublishOptionsArr()));
        logger.info("----- End Selected Properties -----");
    }

    private String[] getPublishOptionsArr()
    {
        List<String> publishOptions = new ArrayList<>();
        if (isUseAlternateParticipantIds())
            publishOptions.add("Use Alternate Participant IDs");
        if (isShiftDates())
            publishOptions.add("Shift Participant Dates");
        publishOptions.add("Include Columns At This PHI Level And Higher: " + getExportPhiLevel().name());
        if (isMaskClinic())
            publishOptions.add("Mask Clinic Names");
        return publishOptions.toArray(new String[publishOptions.size()]);
    }

    private String arrayToString(Object[] arr)
    {
        if (arr == null || arr.length == 0)
            return "None";
        return Arrays.toString(arr);
    }
}
