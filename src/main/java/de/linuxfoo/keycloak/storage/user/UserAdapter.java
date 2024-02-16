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

import jakarta.persistence.EntityManager;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jboss.logging.Logger;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;
import org.keycloak.storage.ReadOnlyException;
import org.keycloak.storage.StorageId;

public class UserAdapter
extends AbstractUserAdapterFederatedStorage
{
    private static final Logger logger = Logger
        .getLogger(UserAdapter.class);
    protected UserEntity entity;
    protected String keycloakId;
    protected EntityManager em;
    private boolean readOnly;

    public UserAdapter(
        KeycloakSession session,
        RealmModel realm,
        ComponentModel model,
        UserEntity entity
    ) {
        super(session, realm, model);

        this.entity = entity;
        this.keycloakId = StorageId.keycloakId(model, entity.getId());
        this.em = session
            .getProvider(JpaConnectionProvider.class, "user-store")
            .getEntityManager();
	String ro = model.getConfig().getFirst("readOnly");
        this.readOnly = ro == null || ro.equals("true");
    }

    public String getPasswordHash() {
        return entity.getPasswordHash();
    }

    public void setPasswordHash(String password_hash) {
        if(readOnly) {
            throw new ReadOnlyException("User is read-only");
        }

        entity.setPasswordHash(password_hash);
        em.persist(entity);
    }

    @Override
    public String getUsername() {
        return entity.getUsername();
    }

    @Override
    public void setUsername(String username) {
        if(readOnly) {
            throw new ReadOnlyException("User is read-only");
        }

        entity.setUsername(username);
    }

    @Override
    public void setCreatedTimestamp(Long createdTimestamp) {
        if(readOnly) {
            throw new ReadOnlyException("User is read-only");
        }

        entity.setCreatedTimestamp(createdTimestamp);
    }

    @Override
    public Long getCreatedTimestamp() {
        return entity.getCreatedTimestamp();
    }

    @Override
    public void setEmail(String email) {
        if(readOnly) {
            throw new ReadOnlyException("User is read-only");
        }

        entity.setEmail(email);
    }

    @Override
    public String getEmail() {
        return entity.getEmail();
    }

    @Override
    public boolean isEmailVerified() {
        return entity.getEmailVerified();
    }

    @Override
    public void setEmailVerified(boolean emailVerified) {
        entity.setEmailVerified(emailVerified);
    }

    @Override
    public String getId() {
        return keycloakId;
    }

    @Override
    public void setSingleAttribute(String name, String value) {
        if(readOnly) {
            throw new ReadOnlyException("User is read-only");
        }

        if (name.equals("firstName")) {
            entity.setFirstName(value);
        } else if (name.equals("lastName")) {
            entity.setLastName(value);
        } else {
            super.setSingleAttribute(name, value);
        }
    }

    @Override
    public void removeAttribute(String name) {
        if(readOnly) {
            throw new ReadOnlyException("User is read-only");
        }

        if (name.equals("firstName")) {
            entity.setFirstName(null);
        } else if (name.equals("lastName")) {
            entity.setLastName(null);
        } else {
            super.removeAttribute(name);
        }
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        if(readOnly) {
            throw new ReadOnlyException("User is read-only");
        }

        if (name.equals("firstName")) {
            entity.setFirstName(values.get(0));
        } else if (name.equals("lastName")) {
            entity.setLastName(values.get(0));
        } else {
            super.setAttribute(name, values);
        }
    }

    @Override
    public String getFirstAttribute(String name) {
        if (name.equals("firstName")) {
            return entity.getFirstName();
        } if (name.equals("lastName")) {
            return entity.getLastName();
        } else {
            return super.getFirstAttribute(name);
        }
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        Map<String, List<String>> attrs = super.getAttributes();
        MultivaluedHashMap<String, String> all = new MultivaluedHashMap<>();
        all.putAll(attrs);
        all.add("firstName", entity.getFirstName());
        all.add("lastName", entity.getLastName());
        return all;
    }

    @Override
    public Stream<String> getAttributeStream(String name) {
        if (name.equals("firstName")) {
            List<String> firstName = new LinkedList<>();
            firstName.add(entity.getFirstName());
            return firstName.stream();
        } else if (name.equals("lastName")) {
            List<String> lastName = new LinkedList<>();
            lastName.add(entity.getLastName());
            return lastName.stream();
        } else {
            return super.getAttributeStream(name);
        }
    }

    @Override
    public Stream<RoleModel> getRoleMappingsStream() {
        Stream<RoleModel> roleMappings = super.getRoleMappingsStream();

        for (ClientRoleEntity clientRole : entity.getClientRoles()) {
            ClientModel client = realm.getClientByClientId(clientRole.getClient());

            if(client==null) {
                logger.warnf(
                    "User %s requests client role %s.%s, " +
                    "but client %s does not exist; " +
                    "client role not assigned.",
                    entity.getUsername(),
                    clientRole.getClient(),
                    clientRole.getRole(),
                    clientRole.getClient()
                );

                continue;
            }

            RoleModel role = client.getRole(clientRole.getRole());

            if(role==null) {
                logger.warnf(
                    "User %s requests client role %s.%s, " +
                    "but client role %s does not exist; " +
                    "client role not assigned.",
                    entity.getUsername(),
                    clientRole.getClient(),
                    clientRole.getRole(),
                    clientRole.getRole()
                );

                continue;
            }

            roleMappings = Stream.concat(roleMappings, Stream.of(role));
        }

        for (RealmRoleEntity realmRole : entity.getRealmRoles()) {
            RoleModel role = realm.getRole(realmRole.getRole());

            if(role==null) {
                logger.warnf(
                    "User %s requests realm role %s, " +
                    "but realm role %s does not exist; " +
                    "realm role not assigned.",
                    entity.getUsername(),
                    realmRole.getRole(),
                    realmRole.getRole()
                );

                continue;
            }

            roleMappings = Stream.concat(roleMappings, Stream.of(role));
        }

        return roleMappings;
    }
}
