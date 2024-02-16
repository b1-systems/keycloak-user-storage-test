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

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@NamedQueries({
    @NamedQuery(
        name="getUserByUsername",
        query="select u from UserEntity u where u.username = :username"
    ),
    @NamedQuery(
        name="getUserByEmail",
        query="select u from UserEntity u where u.email = :email"
    ),
    @NamedQuery(
        name="getUserCount",
        query="select count(u) from UserEntity u"
    ),
    @NamedQuery(
        name="getAllUsers",
        query="select u from UserEntity u"
    ),
    @NamedQuery(
        name="searchForUser",
        query="select u from UserEntity u where " +
              "( lower(u.username) like :search or u.email like :search ) " +
              "order by u.username"
    )
})

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    private String id;
    private String username;
    private String email;
    private boolean email_verified;
    private String password_hash;
    private String firstName;
    private String lastName;
    private Long createdTimestamp;
    @ManyToMany
    @JoinTable(name = "users_to_client_roles",
        joinColumns = { @JoinColumn(name = "user_id") },
        inverseJoinColumns = { @JoinColumn(name = "client_role_id") })
    private Set<ClientRoleEntity> clientRoles = new HashSet<ClientRoleEntity>();
    @ManyToMany
    @JoinTable(name = "users_to_realm_roles",
        joinColumns = { @JoinColumn(name = "user_id") },
        inverseJoinColumns = { @JoinColumn(name = "realm_role_id") })
    private Set<RealmRoleEntity> realmRoles = new HashSet<RealmRoleEntity>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean getEmailVerified() {
        return email_verified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.email_verified = emailVerified;
    }

    public String getPasswordHash() {
        return password_hash;
    }

    public void setPasswordHash(String passwordHash) {
        this.password_hash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Long getCreatedTimestamp() {
        return this.createdTimestamp;
    }

    public void setCreatedTimestamp(Long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public Set<ClientRoleEntity> getClientRoles() {
        return clientRoles;
    }

    public void setClientRoles(Set<ClientRoleEntity> clientRoles) {
        this.clientRoles = clientRoles;
    }

    public Set<RealmRoleEntity> getRealmRoles() {
        return realmRoles;
    }

    public void setRealmRoles(Set<RealmRoleEntity> realmRoles) {
        this.realmRoles = realmRoles;
    }
}
