<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<form action="updateStudyProperties.post" method="POST">
    <table cellspacing="3" class="normal">
        <tr>
            <th>Study Label</th>
            <td><input type="text" size="40" name="label" value="<%= h(getStudy().getLabel()) %>"></td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><%= buttonImg("Update")%>&nbsp;<%= buttonLink("Cancel", "manageStudy.view")%></td>
        </tr>
    </table>
</form>