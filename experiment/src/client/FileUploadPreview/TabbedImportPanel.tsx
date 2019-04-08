import * as React from 'react';
import classNames from 'classnames'
import { Map, List } from 'immutable';
import { Grid } from '@glass/grid';
import { FileAttachmentForm } from "./FileAttachmentForm";

interface FormTabArray extends Array<FormTab>{}

interface FormTab
{
    id: number
    name: string
}

const FILE_UPLOAD_TAB_ID: number = 0;

const FORM_TABS: FormTabArray = [
    {
        id: FILE_UPLOAD_TAB_ID,
        name: 'File Upload'
    },{
        id: 1,
        name: 'Other Import 1 '
    },{
        id: 2,
        name: 'Other Import 2 '
    }];

export class TabbedImportPanel extends React.Component<TabbedImportPanelProps, any> {

    constructor(props) {
        super(props);
        this.state = {
            activeTab: FORM_TABS[FILE_UPLOAD_TAB_ID]
        };

        this.handleTabChange = this.handleTabChange.bind(this);
    }

    handleTabChange(clickedTab: FormTab) {
        const { activeTab } = this.state;
        const { handleFileRemoval } = this.props;

        if (clickedTab !== activeTab) {
            this.setState({
                activeTab: clickedTab
            }, () => {
                if (this.state.activeTab.id !== FILE_UPLOAD_TAB_ID && handleFileRemoval) {
                    handleFileRemoval('');
                }
            })
        }
    }

    renderFileUploadPanel() {
        const {
            handleFileChange,
            handleFileRemoval,
            handleFileSubmit,
        } = this.props;

        return (
            <>
                <FileAttachmentForm
                    acceptedFormats={".tsv, .xlsx, .xls, .csv"}
                    allowMultiple={false}
                    showButtons={false}
                    showLabel={false}
                    showProgressBar={false}
                    onFileChange={handleFileChange}
                    onFileRemoval={handleFileRemoval}
                    onSubmit={handleFileSubmit}
                />
                {this.renderPreviewGrid()}
            </>
        )
    }

    renderPreviewGrid() {
        const { fileUploadPreviewData} = this.props;
        const { activeTab } = this.state;
        if (fileUploadPreviewData && fileUploadPreviewData.size && activeTab.id === FILE_UPLOAD_TAB_ID) {
            let numRows = fileUploadPreviewData.size;

            return (
                <>
                    <strong className={"margin-top block"}>Grid Preview:</strong>
                    <p className={'margin-top'}>The {numRows === 1 ? 'only row ' : 'first ' + numRows + ' rows '} of your data file {numRows === 1 ? 'is' : 'are'} shown below.</p>
                    <Grid
                        data={fileUploadPreviewData}
                        loadingText={"Fetching a preview of your data file"}
                    />
                </>
            )
        }
        else {
            return null;
        }
    }

    render(): React.ReactNode {

        const { activeTab } = this.state;
        return (
            <>
                <FormTabs
                    activeTab={activeTab}
                    onTabChange={this.handleTabChange}
                    tabs={FORM_TABS}
                />
                {activeTab.id === FILE_UPLOAD_TAB_ID && this.renderFileUploadPanel()}
                {activeTab.id === 1 && <h2>Hello other tab 1</h2>}
                {activeTab.id === 2 && <h2>Hello other tab 2</h2>}
            </>
        )
    }
}

interface TabbedImportPanelProps {
    handleFileChange?: (files: Map<string, File>) => any
    handleFileRemoval?: (attachmentName: string) => any
    handleFileSubmit?: (files: Map<string, File>) => any
    fileUploadPreviewData?: List<Map<string, any>>
}

class FormTabs extends React.Component<FormTabsProps, any> {

    constructor(props) {
        super(props);
    }

    handleTabClick(tab: FormTab): any {
        const { onTabChange } = this.props;
        if (onTabChange) {
            onTabChange(tab)
        }
    }

    render(): React.ReactNode {
        const { tabs, activeTab } = this.props;
        return (
            <div className="row">
                <div className="col-sm-12">
                    <ul className="list-group clearfix" style={{listStyle: 'none'}}>
                        {tabs.map(tab => {
                            let isActive = tab === activeTab;
                            return (
                                <li
                                    className={classNames('list-group-item form-step-tab', {'active': isActive})}
                                    key={tab.id}
                                    onClick={() => this.handleTabClick(tab)}>
                                    {tab.name}
                                </li>
                            );
                        })}
                    </ul>
                </div>
            </div>
        );
    }
}

interface FormTabsProps
{
    onTabChange: (tab: FormTab) => any
    tabs: FormTabArray
    activeTab: FormTab
}