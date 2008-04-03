<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.designer.DesignerController" %>
<%@ page import="org.labkey.study.designer.client.model.GWTCohort" %>
<%@ page import="java.util.*" %>
<%
    DesignerController.CreateRepositoryForm form = (DesignerController.CreateRepositoryForm) HttpView.currentModel();
    ActionURL cancelUrl = HttpView.currentContext().cloneActionURL();
    cancelUrl.setAction("cancelWizard.view").replaceParameter("studyId", String.valueOf(form.getStudyId()));
    String species = form.getStudyDefinition().getAnimalSpecies();
    if (null == StringUtils.trimToNull(species))
        species = "animal";
    else
        species = species.toLowerCase();
    if (null != form.getMessage())
    {%>
        <span class="labkey-error"><%=PageFlowUtil.filter(form.getMessage(), true)%></span><br><%
    }%>
Use this wizard to create a folder that will contain all of the assay results and information about each <%=h(species)%> (subject) within
the vaccine study.
<form action="createRepository.view" method="post">
    <input type="hidden" name="studyId" value="<%=form.getStudyId()%>">
    <input type="hidden" name="wizardStepNumber" value="<%=form.getWizardStepNumber()%>">
<%
    if (form.getWizardStep() == DesignerController.WizardStep.PICK_FOLDER)
    {
%>
    <table>
        <tr>
            <td class="ms-vh">Study Begin Date</td>
            <td class="ms-vb"><input name="beginDateStr" value="<%=h(form.getBeginDateStr())%>"></td>
        </tr>
        <tr>
        <td class="ms-vh">Folder Name</td>
        <td class="ms-vb"><input name="folderName" value="<%=PageFlowUtil.filter(form.getFolderName())%>"></td>
        </tr>
        <tr>
            <td class="ms-vh">Parent Folder</td>
            <td class="ms-vb">
                <select name="parentFolder">
            <%
                Set<Container> writableContainers = ContainerManager.getContainerSet(ContainerManager.getContainerTree(), HttpView.currentContext().getUser(), ACL.PERM_ADMIN);
                SortedSet<Container> sortedContainers = new TreeSet<Container>(new Comparator<Container>()
                {
                    public int compare(Container o1, Container o2)
                    {
                        return o1.getPath().compareTo(o2.getPath());
                    }
                });
                sortedContainers.addAll(writableContainers);
                for (Container c : sortedContainers)
                {
            %>
                    <option value="<%=c.getId()%>" <%=c.equals(form.getParentFolder()) ? "selected" : ""%>><%=h(c.getPath())%></option>
            <%  } %>
                </select>
            </td>
        </tr>
    </table>
    <%=PageFlowUtil.buttonLink("Back", "javascript:window.history.back();")%> <input type="image" src="<%=PageFlowUtil.buttonSrc("Next")%>">&nbsp;&nbsp;<%=PageFlowUtil.buttonLink("Cancel", cancelUrl)%>

<%
}
else if (form.getWizardStep() == DesignerController.WizardStep.SHOW_SAMPLES)
{
%>
Each study needs specimen ids for the specimens included in the study. To upload the
    specimens, follow the instructions below.<br>
<ol>
    <li>Download the specimen spreadsheet <%
    ActionURL xlUrl = HttpView.currentContext().cloneActionURL().setAction("getSpecimenExcel");
%>
<%=textLink("Download Excel Workbook", xlUrl)%><br>
</li>
    <li>Save the spreadsheet to your computer</li>
    <li>Fill in the specimen spreadsheet. The following columns must be filled in
        <ul>
            <li>Subject -- Unique identifier for the <%=species%></li>
            <li>Day -- The day number when the sample was drawn</li>
        </ul>
    </li>
    <li>Paste data copied from the downloaded workbook and paste in the box below.</li>
</ol>
    <br>
    <textarea style="border:1px solid black;" rows="20" cols="80" name="specimenTSV"><%=h(form.getSpecimenTSV())%></textarea>
        <input type="hidden" value="false"  name="ignoreWarnings">
    <br>
    <%=PageFlowUtil.buttonLink("Back", "javascript:window.history.back();")%>
        <% if (form.isContainsWarnings())  {%>
            <input type="image" src="<%=PageFlowUtil.buttonSrc("Ignore Warnings and Continue")%>" onclick="form.ignoreWarnings.value = 'true';form.submit();">
        <%}%>
        <input type="image" src="<%=PageFlowUtil.buttonSrc("Next")%>">&nbsp;&nbsp;<%=PageFlowUtil.buttonLink("Cancel", cancelUrl)%>
<br><%
%>
<input type="hidden" name="uploadSpecimens" value="true"> <br>
<%
}
else if (form.getWizardStep() == DesignerController.WizardStep.UPLOAD_SAMPLES)
{
%>
Paste a tab-delimited dataset copied from the workbook downloaded in the previous set. Copy the area
    containing specimen information and paste it here.<br>
<textarea style="border:1px solid black;" rows="20" cols="80" name="specimenTSV"><%=h(form.getSpecimenTSV())%></textarea>
    <input type="hidden" value="false"  name="ignoreWarnings">
<br>
<%=PageFlowUtil.buttonLink("Back", "javascript:window.history.back();")%>
    <% if (form.isContainsWarnings())  {%>
        <input type="image" src="<%=PageFlowUtil.buttonSrc("Ignore Warnings and Continue")%>" onclick="form.ignoreWarnings.value = 'true';form.submit();">
    <%}%>
    <input type="image" src="<%=PageFlowUtil.buttonSrc("Next")%>">&nbsp;&nbsp;<%=PageFlowUtil.buttonLink("Cancel", cancelUrl)%>
<%
}
else if (form.getWizardStep() == DesignerController.WizardStep.SHOW_PARTICIPANTS)
{
    List<GWTCohort> groups = form.getStudyDefinition().getGroups();
    int nParticipants = 0;
    for (GWTCohort group : groups)
        nParticipants += group.getCount();
%>
    This study defines <%=form.getStudyDefinition().getGroups().size()%> cohorts with a total of
    <%=nParticipants%> subjects.
    <%
        ActionURL xlUrl = HttpView.currentContext().cloneActionURL().setAction("getParticipantExcel");
    %>
    <br>
    To initiate this study, you will need to fill out an excel workbook with the subject id and cohort for each <%=h(species)%>.
    <ul>
        <li><a href="<%=h(xlUrl)%>">Download the Excel Workbook with subjects</a></li>
        <li>Save the workbook on your computer</li>
        <li>Fill in identifiers for each subject in the study</li>
        <li>You can also add new columns to this list containing other information about each subject.</li>
        <li>Upload your own set of subject data by pasting spreadsheet data in the following text field. The first
        row of the data must contain the following columns.</li>
        <ul>
            <li>SubjectId -- The id for a particular <%=h(species)%>.</li>
            <li>Cohort -- The cohort name. Must be a cohort in the study design</li>
            <li>StartDate -- The date this subject started the study (Day 0)</li>
            <li>Other columns may be added as well</li>
         </ul>
    </ul>
    <br><%
    %>
    <input type="hidden" name="uploadParticipants" value="true">
    The correct number of participants should be supplied for each cohort.<br>
    <textarea rows="20" cols="60" name="participantTSV"><%=h(form.getParticipantTSV())%></textarea>
    <input type="hidden" name="ignoreWarnings" value="false"><br>
    <%=PageFlowUtil.buttonLink("Back", "javascript:window.history.back();")%>     <% if (form.isContainsWarnings())  {%>
        <input type="image" src="<%=PageFlowUtil.buttonSrc("Ignore Warnings and Continue")%>" onclick="form.ignoreWarnings.value = 'true';form.submit();">
    <%}%>
<input type="image" src="<%=PageFlowUtil.buttonSrc("Next")%>">&nbsp;&nbsp;<%=PageFlowUtil.buttonLink("Cancel", cancelUrl)%>
<%
}
else if (form.getWizardStep() == DesignerController.WizardStep.UPLOAD_PARTICIPANTS)
{
%>
    You can upload your own set of subject data by pasting spreadsheet data in the following text field. The first
    row of the data must contain the following columns.
    <ul>
        <li>SubjectId -- The id for a particular <%=h(species)%>.</li>
        <li>Cohort -- The cohort name. Must be a cohort in the study design</li>
     </ul>
    Other columns may be added. The correct number of participants should be supplied for each cohort.<br>
    <textarea rows="20" cols="60" name="participantTSV"><%=h(form.getParticipantTSV())%></textarea>
    <input type="hidden" name="ignoreWarnings" value="false"><br>
    <%=PageFlowUtil.buttonLink("Back", "javascript:window.history.back();")%>     <% if (form.isContainsWarnings())  {%>
        <input type="image" src="<%=PageFlowUtil.buttonSrc("Ignore Warnings and Continue")%>" onclick="form.ignoreWarnings.value = 'true';form.submit();">
    <%}%>
<input type="image" src="<%=PageFlowUtil.buttonSrc("Next")%>">&nbsp;&nbsp;<%=PageFlowUtil.buttonLink("Cancel", cancelUrl)%><%
    }
    else
    {
        List<GWTCohort> groups = form.getStudyDefinition().getGroups();
        int nParticipants = 0;
        for (GWTCohort group : groups)
            nParticipants += group.getCount();
        HashSet<Object> specimenPtids = new HashSet<Object>();
    %>
    You are about to create a study folder with the following settings:
    <ul>
       <li><b>Folder Name: </b><%=h(form.getFolderName())%> </li>
        <li><b>Start Date: </b><%=h(form.getBeginDateStr())%></li>
        <li><b>Subjects: </b><%=h(form.getParticipants().length)%> <%
            if (nParticipants != form.getParticipants().length) { %>
                <span class="labkey-error">Warning: Study design called for <%=nParticipants%> subjects.</span>
            <%}
        %></li>
        <li><b>Specimens: </b><%=h(form.getSpecimens().length)%> </li> 
    </ul>
    <br>
    <%=PageFlowUtil.buttonLink("Back", "javascript:window.history.back();")%> <input type="image" src="<%=PageFlowUtil.buttonSrc("Finish")%>">&nbsp;&nbsp;<%=PageFlowUtil.buttonLink("Cancel", cancelUrl)%>
    <%
    }
    %>

</form>
