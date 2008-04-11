<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.designer.DesignerController" %>
<%@ page import="org.labkey.study.designer.client.model.GWTCohort" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%
    DesignerController.CreateRepositoryForm form = (DesignerController.CreateRepositoryForm) HttpView.currentModel();
    Container container = HttpView.currentContext().getContainer();
    String species = DesignerController.getStudyDefinition(form, container).getAnimalSpecies();
    ActionURL cancelUrl = new ActionURL(DesignerController.CancelWizardAction.class, container).addParameter("studyId", String.valueOf(form.getStudyId()));
    if (null != form.getMessage())
    {%>
        <span class="labkey-error"><%=PageFlowUtil.filter(form.getMessage(), true)%></span><br><%
    }%>
Use this wizard to create a folder that will contain all of the assay results and information about each <%=h(species)%> (subject) within
the vaccine study.

<form name="createRepositoryForm" action="createRepository.view" method="post">
    <input type="hidden" name="studyId" value="<%=form.getStudyId()%>">
    <input type="hidden" name="studyName" value="<%=h(form.getStudyName())%>">
    <input type="hidden" name="wizardStepNumber" value="<%=form.getWizardStepNumber()%>">
<%
    if (form.getWizardStep() == DesignerController.WizardStep.PICK_FOLDER)
    {
%>
    <table>
        <tr>
            <td class="ms-vh">Study Begin Date</td>
            <td class="ms-vb"><input name="beginDate" value="<%=DateUtil.formatDate(form.getBeginDate())%>"></td>
        </tr>
        <tr>
        <td class="ms-vh">Folder Name</td>
        <td class="ms-vb"><input name="folderName" value="<%=PageFlowUtil.filter(form.getFolderName())%>"></td>
        </tr>
        <tr>
            <td class="ms-vh">Parent Folder</td>
            <td class="ms-vb">
                <select name="parentFolderId">
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
                    <option value="<%=c.getId()%>" <%=c.getId().equals(form.getParentFolderId()) ? "selected" : ""%>><%=h(c.getPath())%></option>
            <%  } %>
                </select>
            </td>
        </tr>
    </table>
    <%=PageFlowUtil.buttonLink("Back", "javascript:window.history.back();")%> <input type="image" src="<%=PageFlowUtil.buttonSrc("Next")%>">&nbsp;&nbsp;<%=PageFlowUtil.buttonLink("Cancel", cancelUrl)%>

<%
}
else
{
%>
    <input type="hidden" name="beginDate" value="<%=form.getBeginDate()%>">
    <input type="hidden" name="folderName" value="<%=h(form.getFolderName())%>">
    <input type="hidden" name="parentFolderId" value="<%=form.getParentFolderId()%>">
<%
}
if (form.getWizardStep() == DesignerController.WizardStep.SHOW_SAMPLES)
{
%>
Each study needs specimen ids for the specimens included in the study. To upload the
    specimens, follow the instructions below.<br>
<ol>
    <li>Download the specimen spreadsheet
        [<a href="#downloadSpecimens" onclick="sendFormTo('getSpecimenExcel.view')">Download Excel Workbook</a>]<br>
</li>
    <li>Save the spreadsheet to your computer</li>
    <li>Fill in the specimen spreadsheet. The following columns must be filled in
        <ul>
            <li>Subject -- Unique identifier for the <%=h(species)%></li>
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
if (form.getWizardStep() == DesignerController.WizardStep.UPLOAD_SAMPLES)
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
if (form.getWizardStep() != DesignerController.WizardStep.UPLOAD_SAMPLES &&
        form.getWizardStep() != DesignerController.WizardStep.SHOW_SAMPLES)
{
%>
    <input type="hidden" name="specimenTSV" value="<%=h(form.getSpecimenTSV())%>">
<%
}
if (form.getWizardStep() == DesignerController.WizardStep.SHOW_PARTICIPANTS)
{
    List<GWTCohort> groups = DesignerController.getStudyDefinition(form, container).getGroups();
    int nParticipants = 0;
    for (GWTCohort group : groups)
        nParticipants += group.getCount();
%>
    This study defines <%=DesignerController.getStudyDefinition(form, container).getGroups().size()%> cohorts with a total of
    <%=nParticipants%> subjects.
    <%
        ActionURL xlUrl = HttpView.currentContext().cloneActionURL().setAction(DesignerController.GetParticipantExcelAction.class);
    %>
    <br>
    To initiate this study, you will need to fill out an excel workbook with the subject id and cohort for each <%=h(species)%>.
    <ul>
        <li><a href="#downloadSubjects" onclick="sendFormTo('getParticipantExcel.view')">Download the Excel Workbook with subjects</a></li>
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
if (form.getWizardStep() == DesignerController.WizardStep.UPLOAD_PARTICIPANTS)
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
if (form.getWizardStep() == DesignerController.WizardStep.CONFIRM)
{
    List<GWTCohort> groups = DesignerController.getStudyDefinition(form, container).getGroups();
    int nParticipants = 0;
    for (GWTCohort group : groups)
        nParticipants += group.getCount();
%>
You are about to create a study folder with the following settings:
<ul>
   <li><b>Folder Name: </b><%=h(form.getFolderName())%> </li>
    <li><b>Start Date: </b><%=h(form.getBeginDate())%></li>
    <li><b>Subjects: </b><%=h(DesignerController.getParticipants().length)%> <%
        if (nParticipants != DesignerController.getParticipants().length) { %>
            <span class="labkey-error">Warning: Study design called for <%=nParticipants%> subjects.</span>
        <%}
    %></li>
    <li><b>Specimens: </b><%=h(DesignerController.getSpecimens().length)%> </li>
</ul>
<br>
<%=PageFlowUtil.buttonLink("Back", "javascript:window.history.back();")%> <input type="image" src="<%=PageFlowUtil.buttonSrc("Finish")%>">&nbsp;&nbsp;<%=PageFlowUtil.buttonLink("Cancel", cancelUrl)%>
<%
}

if (form.getWizardStep() != DesignerController.WizardStep.UPLOAD_PARTICIPANTS &&
        form.getWizardStep() != DesignerController.WizardStep.SHOW_PARTICIPANTS)
{
%>
    <input type="hidden" name="participantTSV" value="<%=h(form.getParticipantTSV())%>">
<%
}
%>

</form>

<!-- Allows us to override the form destination, so that other actions can receive the form -->
<script type="text/javascript">
    function sendFormTo(actionName)
    {
        oldActionName = document.forms.createRepositoryForm.action;
        document.forms.createRepositoryForm.action=actionName;
        document.forms.createRepositoryForm.submit();
        document.forms.createRepositoryForm.action=oldActionName;
    }
</script>
