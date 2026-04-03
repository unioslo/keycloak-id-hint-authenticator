/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.uio.keycloak.authenticator.idhint2fa;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.ConfigurableAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:franciaa@uio.no">Francis Augusto Medeiros-Logeay</a>
 * @version $Revision: 1 $
 */
public class IdHint2faAuthenticatorFactory implements AuthenticatorFactory, ConfigurableAuthenticatorFactory {

    public static final String PROVIDER_ID = "id-hint-authenticator";
    private static final IdHint2faAuthenticator SINGLETON = new IdHint2faAuthenticator();

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    private static AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
    };
    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<ProviderConfigProperty>();

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName("jkws-uri");
        property.setLabel("Jkws uri");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("The jwks to validate an id token hint.");
        configProperties.add(property);
    }


    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName("attribute-matching");
        property.setLabel("Local user attribute");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("The user attribute to match the id token hint to.");
        configProperties.add(property);
    }

    @Override
    public String getHelpText() {
        return "Sets the user based on the id token hint.";
    }

    @Override
    public String getDisplayType() {
        return "ID Token hint";
    }

    @Override
    public String getReferenceCategory() {
        return "2fa";
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }


}
