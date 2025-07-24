package org.apache.zeppelin.rest.bde.view.auth;

import com.gable.templar.heaven.service.rest.cerberus.view.OAuthGetUserResponse;
import com.gable.templar.heaven.service.rest.cerberus.view.OAuthResponse;

import java.io.Serializable;

/**
 * Copyright (c) 2019. Chailuk Chanthawit
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 * <p>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * <p>
 * Created by chailuk.c on 21/2/2019 AD.
 */
public class LoginResponse implements Serializable {
    private OAuthResponse oauth;
    private OAuthGetUserResponse oauthUser;

    public OAuthResponse getOauth() {
        return oauth;
    }

    public void setOauth(OAuthResponse oauth) {
        this.oauth = oauth;
    }

    public OAuthGetUserResponse getOauthUser() {
        return oauthUser;
    }

    public void setOauthUser(OAuthGetUserResponse oauthUser) {
        this.oauthUser = oauthUser;
    }
}
