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
import jakarta.persistence.TypedQuery;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.UUID;
import org.apache.commons.codec.digest.Crypt;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.cache.CachedUserModel;
import org.keycloak.models.cache.OnUserCache;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.ReadOnlyException;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

public class UserStorageTestProvider
implements
    UserStorageProvider,
    UserLookupProvider,
    UserRegistrationProvider,
    UserQueryProvider,
    CredentialInputUpdater,
    CredentialInputValidator,
    OnUserCache
{
    private static final Logger logger = Logger
        .getLogger(UserStorageTestProvider.class);
    public static final String PASSWORD_HASH_CACHE_KEY = UserAdapter
        .class
        .getName() + ".passwordHash";
    protected EntityManager em;
    protected ComponentModel model;
    protected KeycloakSession session;
    private boolean readOnly;

    UserStorageTestProvider(
        KeycloakSession session,
        ComponentModel model
    ) {
        this.session = session;
        this.model = model;
        this.readOnly =
            model.get("readOnly")==null ||
            model.get("readOnly").equals("true");
        this.em = session
            .getProvider(JpaConnectionProvider.class, "user-store")
            .getEntityManager();
    }

    @Override
    public void preRemove(RealmModel realm) {
    }

    @Override
    public void preRemove(RealmModel realm, GroupModel group) {
    }

    @Override
    public void preRemove(RealmModel realm, RoleModel role) {
    }

    @Override
    public void close() {
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String persistenceId = StorageId.externalId(id);
        UserEntity entity = em.find(UserEntity.class, persistenceId);

        if (entity == null) {
            logger.info("could not find user by id: " + id);

            return null;
        }

        return new UserAdapter(session, realm, model, entity);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        TypedQuery<UserEntity> query = em
            .createNamedQuery("getUserByUsername", UserEntity.class);
        query.setParameter("username", username);
        List<UserEntity> result = query.getResultList();

        if (result.isEmpty()) {
            logger.info("could not find username: " + username);

            return null;
        }

        return new UserAdapter(session, realm, model, result.get(0));
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        TypedQuery<UserEntity> query = em
            .createNamedQuery("getUserByEmail", UserEntity.class);
        query.setParameter("email", email);
        List<UserEntity> result = query.getResultList();

        if (result.isEmpty()) {
            return null;
        }

        return new UserAdapter(session, realm, model, result.get(0));
    }

    @Override
    public UserModel addUser(RealmModel realm, String username) {
        if(readOnly) {
            throw new ReadOnlyException("Storage is read-only");
        }

        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUsername(username);

        em.persist(entity);

        logger.info("added user: " + username);

        return new UserAdapter(session, realm, model, entity);
    }

    @Override
    public boolean removeUser(RealmModel realm, UserModel user) {
        if(readOnly) {
            throw new ReadOnlyException("Storage is read-only");
        }

        String persistenceId = StorageId.externalId(user.getId());
        UserEntity entity = em.find(UserEntity.class, persistenceId);

        if (entity == null) {
            return false;
        }

        em.remove(entity);

        return true;
    }

    @Override
    public void onCache(
        RealmModel realm,
        CachedUserModel user,
        UserModel delegate
    ) {
        String password_hash = ((UserAdapter)delegate).getPasswordHash();

        if (password_hash != null) {
            user.getCachedWith().put(PASSWORD_HASH_CACHE_KEY, password_hash);
        }
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    @Override
    public boolean updateCredential(
        RealmModel realm,
        UserModel user,
        CredentialInput input
    ) {
        if (!supportsCredentialType(input.getType()) ||
            !(input instanceof UserCredentialModel)) {
            return false;
        } else if(readOnly) {
            throw new ReadOnlyException("Storage is read-only");
        }

        UserCredentialModel cred = (UserCredentialModel)input;
        UserAdapter adapter = getUserAdapter(user);
        adapter.setPasswordHash(Crypt.crypt(cred.getValue()));

        return true;
    }

    public UserAdapter getUserAdapter(UserModel user) {
        if (user instanceof CachedUserModel) {
            return (UserAdapter)((CachedUserModel) user)
                .getDelegateForUpdate();
        } else {
            return (UserAdapter) user;
        }
    }

    @Override
    public void disableCredentialType(
        RealmModel realm,
        UserModel user,
        String credentialType
    ) {
        if (!supportsCredentialType(credentialType)) {
            return;
        } else if(readOnly) {
            throw new ReadOnlyException("Storage is read-only");
        }

        getUserAdapter(user).setPasswordHash(null);
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream(
        RealmModel realm,
        UserModel user
    ) {
        if (getUserAdapter(user).getPasswordHash() != null) {
            Set<String> set = new HashSet<>();
            set.add(PasswordCredentialModel.TYPE);
            return set.stream();
        } else {
            return Stream.empty();
        }
    }

    @Override
    public boolean isConfiguredFor(
        RealmModel realm,
        UserModel user,
        String credentialType
    ) {
        return
            supportsCredentialType(credentialType) &&
            getPasswordHash(user) != null;
    }

    @Override
    public boolean isValid(
        RealmModel realm,
        UserModel user,
        CredentialInput input
    ) {
        if (!supportsCredentialType(input.getType()) ||
            !(input instanceof UserCredentialModel)) {
            return false;
        }

        UserCredentialModel cred = (UserCredentialModel)input;
        String password_hash = getPasswordHash(user);

        return
            password_hash != null &&
            password_hash
                .equals(Crypt.crypt(cred.getValue(), password_hash));
    }

    public String getPasswordHash(UserModel user) {
        String password_hash = null;

        if (user instanceof CachedUserModel) {
            password_hash = (String)((CachedUserModel)user)
                .getCachedWith()
                .get(PASSWORD_HASH_CACHE_KEY);
        } else if (user instanceof UserAdapter) {
            password_hash = ((UserAdapter)user)
                .getPasswordHash();
        }

        return password_hash;
    }

    @Override
    public int getUsersCount(RealmModel realm) {
        Object count = em.createNamedQuery("getUserCount")
            .getSingleResult();

        return ((Number)count).intValue();
    }

    @Override
    public Stream<UserModel> searchForUserStream(
        RealmModel realm,
        Map<String, String> params,
        Integer firstResult,
        Integer maxResults
    ) {
        String search = params.get(UserModel.SEARCH);

        if(search==null) {
            search = "";
        }

        TypedQuery<UserEntity> query = em
            .createNamedQuery("searchForUser", UserEntity.class);

        query.setParameter(
            "search",
            "%" + search.toLowerCase().replace("*","%") + "%"
        );

        if (firstResult != null) {
            query.setFirstResult(firstResult);
        }

        if (maxResults != null) {
            query.setMaxResults(maxResults);
        }

        return query.getResultStream()
            .map(entity -> new UserAdapter(session, realm, model, entity));
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(
        RealmModel realm,
        GroupModel group,
        Integer firstResult,
        Integer maxResults
    ) {
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(
        RealmModel realm,
        String attrName,
        String attrValue
    ) {
        return Stream.empty();
    }

    public boolean getReadOnly() {
        return readOnly;
    }	    
}
