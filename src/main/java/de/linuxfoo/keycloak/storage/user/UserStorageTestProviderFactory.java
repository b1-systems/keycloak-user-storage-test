/* Copyright 2024  B1 Systems GmbH <info@b1-systems.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * */

package de.linuxfoo.keycloak.storage.user;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;
import java.util.ArrayList;
import java.util.List;

public class UserStorageTestProviderFactory
implements UserStorageProviderFactory<UserStorageTestProvider>
{
    public static final String PROVIDER_ID = "user-storage-test";
    private static final Logger logger = Logger
        .getLogger(UserStorageTestProviderFactory.class);
    private static List<ProviderConfigProperty> configProperties =
        new ArrayList<ProviderConfigProperty>();

    @Override
    public UserStorageTestProvider create(
        KeycloakSession session,
        ComponentModel model
    ) {
        configProperties = ProviderConfigurationBuilder.create()
          .property()
            .name("readOnly")
            .label("Read-only")
            .type(ProviderConfigProperty.BOOLEAN_TYPE)
            .defaultValue(true)
            .helpText(
                "If set to ON, this provider is read-only, " +
                "users can not be added or deleted, and no user properties " +
                "or attributes can be modified)."
            )
            .add()
          .build();
        return new UserStorageTestProvider(session, model);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "User Storage Test Provider";
    }

    @Override
    public void close() {
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }
}
