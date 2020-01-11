import React, {PureComponent} from "react";
import {FileAttachmentForm} from "@labkey/components";
import {ActionURL} from "@labkey/api";

import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faImage, faTimesCircle} from "@fortawesome/free-solid-svg-icons";

export default class SSOFields extends PureComponent<any, any> {
    render() {
        return(
            <div>
                <ImageAndFileAttachmentForm
                    text="Page Header Logo"
                    imageUrl={ActionURL.getBaseURL(true) + this.props.headerLogoUrl}
                    onFileChange={this.props.onFileChange}
                    handleDeleteLogo={this.props.handleDeleteLogo}
                    fileTitle='auth_header_logo'
                    canEdit={this.props.canEdit}
                />

                <div className="sso-fields__spacer"/>

                 <ImageAndFileAttachmentForm
                        text="Login Page Logo"
                        imageUrl={ActionURL.getBaseURL(true) + this.props.loginLogoUrl}
                        onFileChange={this.props.onFileChange}
                        handleDeleteLogo={this.props.handleDeleteLogo}
                        fileTitle='auth_login_page_logo'
                        canEdit={this.props.canEdit}
                 />
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
            <div className="sso-fields__null-image">
                <FontAwesomeIcon icon={faImage} color={"#DCDCDC"} size={"6x"}/>
                <div className="sso-fields__null-image__text">None selected</div>
            </div>;

        const img =
            <img
                className="sso-fields__image"
                src={this.state.imageUrl}
                onError={() => {this.setState({imageUrl: null})}}
                alt="Sign in"
            />;

        return(
            <>
                <div className="sso-fields__label">
                    {this.props.text}
                </div>

                { this.props.canEdit ?
                    <>
                        <div className="sso-fields__image-holder">
                            {this.state.imageUrl ?
                                <>
                                    {img}
                                    <FontAwesomeIcon className="sso-fields__delete-img"
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


                        <div className="sso-fields__file-attachment" id={this.props.fileTitle}>
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
                    : <div className="sso-fields__image-holder--view-only"> {this.state.imageUrl ? img : noImageSelectedDisplay} </div>
                }
            </>
        );
    }
}