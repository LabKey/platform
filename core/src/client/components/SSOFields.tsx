import React, {PureComponent} from "react";
import {FileAttachmentForm, importData} from "@labkey/components";
import {ActionURL} from "@labkey/api";

import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faImage, faTimesCircle} from "@fortawesome/free-solid-svg-icons";

export default class SSOFields extends PureComponent<any, any> {
    render() {
        return(
            <div >
                <div className="">
                    <ImageAndFileAttachmentForm
                        text="Page Header Logo"
                        imageUrl={ActionURL.getBaseURL(true) + this.props.headerLogoUrl}
                        onFileChange={this.props.onFileChange}
                        handleDeleteLogo={this.props.handleDeleteLogo}
                        fileTitle='auth_header_logo'
                        canEdit={this.props.canEdit}
                    />
                </div>

                <br/>

                <div className="">
                    <ImageAndFileAttachmentForm
                        text="Login Page Logo"
                        imageUrl={ActionURL.getBaseURL(true) + this.props.loginLogoUrl}
                        onFileChange={this.props.onFileChange}
                        handleDeleteLogo={this.props.handleDeleteLogo}
                        fileTitle='auth_login_page_logo'
                        canEdit={this.props.canEdit}
                    />
                </div>
            </div>
        );
    }
}

class ImageAndFileAttachmentForm extends PureComponent<any, any>{
    constructor(props) {
        super(props);
        this.state = {
            imageUrl: this.props.imageUrl,
        };
    }

    render() {
        const noImageSelectedDisplay =
            <div className="nullImage">
                <FontAwesomeIcon icon={faImage} color={"#DCDCDC"} size={"6x"}/>
                <div className="fileAttachmentNoImageText">None selected</div>
            </div>;

        const img =
            <img
                className="fileAttachmentImageDisplay"
                src={this.state.imageUrl}
                onError={() => {this.setState({imageUrl: null})}}
                alt="Sign in"
            />;

        return(
            <>
                <div className="fileAttachmentLabel">
                    {this.props.text}
                </div>

                { this.props.canEdit ?
                    <>
                        <div className="fileAttachmentImage">
                            {this.state.imageUrl ?
                                <>
                                    {img}
                                    <FontAwesomeIcon className="fileAttachmentDeleteImage"
                                                     icon={faTimesCircle}
                                                     color={"#d9534f"}
                                                     onClick={() => {
                                                        this.setState({imageUrl: null});
                                                        this.props.handleDeleteLogo(this.props.fileTitle);
                                                     }}
                                    />
                                </>
                                : noImageSelectedDisplay
                            }
                        </div>


                        <div className="fileAttachmentComponent">
                            <FileAttachmentForm
                                key={this.props.text}
                                showLabel={false}
                                allowMultiple={false}
                                allowDirectories={false}
                                acceptedFormats={".jpeg,.png,.gif,.tif"}
                                showAcceptedFormats={false}
                                onFileChange={(attachment) => {
                                    this.props.onFileChange(attachment, this.props.fileTitle)
                                }}
                            />
                        </div>
                    </>
                    : <div className="viewOnlyFloatRight"> {img} </div>
                }
            </>
        );
    }
}