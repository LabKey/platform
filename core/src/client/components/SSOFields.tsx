import React, { PureComponent } from 'react';
import { FileAttachmentForm } from '@labkey/components';
import { ActionURL } from '@labkey/api';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faImage, faTimesCircle } from '@fortawesome/free-solid-svg-icons';

interface Props {
    headerLogoUrl?: string;
    loginLogoUrl?: string;
    onFileChange?: Function;
    handleDeleteLogo?: Function;
    canEdit?: boolean;
}

export default class SSOFields extends PureComponent<Props> {
    render() {
        return (
            <>
                <ImageAndFileAttachmentForm
                    text="Page Header Logo"
                    imageUrl={ActionURL.getBaseURL(true) + this.props.headerLogoUrl}
                    onFileChange={this.props.onFileChange}
                    handleDeleteLogo={this.props.handleDeleteLogo}
                    fileTitle="auth_header_logo"
                    canEdit={this.props.canEdit}
                    index={1}
                />

                <div className="sso-fields__spacer"/>

                <ImageAndFileAttachmentForm
                    text="Login Page Logo"
                    imageUrl={ActionURL.getBaseURL(true) + this.props.loginLogoUrl}
                    onFileChange={this.props.onFileChange}
                    handleDeleteLogo={this.props.handleDeleteLogo}
                    fileTitle="auth_login_page_logo"
                    canEdit={this.props.canEdit}
                    index={2}
                />
            </>
        );
    }
}

interface ImgFileAttachForm_Props {
    text: string;
    imageUrl: string;
    onFileChange?: Function;
    handleDeleteLogo?: Function;
    fileTitle: string;
    canEdit: boolean;
    index: number;
}

interface ImgFileAttachForm_State {
    imageUrl: string;
}

class ImageAndFileAttachmentForm extends PureComponent<ImgFileAttachForm_Props, ImgFileAttachForm_State> {
    constructor(props) {
        super(props);
        this.state = {
            imageUrl: this.props.imageUrl,
        };
    }

    render() {
        const noImageSelectedDisplay = (
            <div className="sso-fields__null-image">
                <FontAwesomeIcon icon={faImage} color="#DCDCDC" size="6x" />
                <div className="sso-fields__null-image__text">None selected</div>
            </div>
        );

        const img = (
            <img
                className={
                    this.props.fileTitle == 'auth_header_logo'
                        ? 'sso-fields__image__header-logo'
                        : 'sso-fields__image__page-logo'
                }
                src={this.state.imageUrl}
                onError={() => {
                    this.setState({ imageUrl: null });
                }}
                alt="Sign in"
            />
        );

        return (
            <div className={this.props.fileTitle + "_landing"}>
                <div className="sso-fields__label">{this.props.text}</div>

                {this.props.canEdit ? (
                    <>
                        <div className="sso-fields__image-holder">
                            {this.state.imageUrl ? (
                                <div>
                                    <div>{img}</div>
                                    <FontAwesomeIcon
                                        className={
                                            this.props.fileTitle == 'auth_header_logo'
                                                ? 'sso-fields__delete-img--header-logo'
                                                : 'sso-fields__delete-img--page-logo'
                                        }
                                        icon={faTimesCircle}
                                        color="#d9534f"
                                        onClick={() => {
                                            this.setState({ imageUrl: null });
                                            this.props.handleDeleteLogo(this.props.fileTitle);
                                        }}
                                    />
                                </div>
                            ) : (
                                noImageSelectedDisplay
                            )}
                        </div>

                        <div className="sso-fields__file-attachment" id={this.props.fileTitle}>
                            <FileAttachmentForm
                                key={this.props.text}
                                index={this.props.index}
                                showLabel={false}
                                allowMultiple={false}
                                allowDirectories={false}
                                acceptedFormats=".jpg,.jpeg,.png,.gif,.tif"
                                showAcceptedFormats={false}
                                onFileChange={attachment => {
                                    this.props.onFileChange(attachment, this.props.fileTitle);
                                }}
                            />
                        </div>
                    </>
                ) : (
                    <div className="sso-fields__image-holder--view-only">
                        {this.state.imageUrl ? img : noImageSelectedDisplay}
                    </div>
                )}
            </div>
        );
    }
}
