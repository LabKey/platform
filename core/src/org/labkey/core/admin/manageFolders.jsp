<//www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController.ManageFoldersForm"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%
    HttpView<ManageFoldersForm> me = (HttpView<ManageFoldersForm>) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();
    Container project = c.getProject();
%>
<div id="someUniqueElement"></div>
<script type="text/javascript">
    LABKEY.requiresExt4Sandbox();
    LABKEY.requiresCss('study/DataViewsPanel.css');
    LABKEY.requiresScript("admin/FolderManagementPanel.js");
</script>
<script type="text/javascript">

    Ext4.onReady(function() {

        var folderPanel = Ext4.create('LABKEY.ext.panel.FolderManagementPanel', {
            renderTo : 'someUniqueElement',
            height   : 700,
            selected : <%= c.getRowId() %>,
            requiredPermission : <%=PageFlowUtil.jsString(RoleManager.getPermission(AdminPermission.class).getUniqueName())%>
        });

        var _resize = function(w, h) {
            if (!folderPanel.rendered)
                return;

            LABKEY.Utils.resizeToViewport(folderPanel, w, h);
        };

        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();

    });
</script>