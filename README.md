# A Keycloak Custom User Storage Provider for Relational Databases

## 1 Overview

### 1.1 Introduction

When using the Identity Provider Keycloak[^1], the primary data sources for
identities are the internal database and user federation via LDAP and Kerberos.

There is a user storage service provider interface[^2] providing the
possibility to develop Keycloak plugins supporting other datasource types.

This storage provider can import users, realm- and client roles from the
pre-existing database into a Keycloak realm. The target roles that users will
be mapped to must have been created beforehand in the realm; missing roles will
lead to warning messages in the Keycloak log.

### 1.2 About the External Database

The examples in this proposal assume a pre-existing database `userdb` with the
following tables:

* `users` contains rows of identities with the following attributes:
  - a (preferred) username,
  - a creation datetime information,
  - the first name of the user (can be empty),
  - the last name of the user (can be empty),
  - an email address of the user (can be empty) and
  - a salted password hash (if NULL, no password is associated with the user).

* `client_roles` contains rows of client-IDs and client role names.

* A mapping table assigns zero or more client roles to each user.

* `realm_roles` contains rows of realm role names.

* A mapping table assigns zero or more realm roles to each user.

The configuration and the example SQL data presented here demonstrate this
setup for a PostGreSQL database (tested with postgres 15), but they can be
modified to support MariaDB, MySQL and other database types.

### 1.3 See Also

The storage provider presented in this document attaches a pre-existing
relational database of identities and role mappings to a Keycloak realm. It
uses the Java Persistence API and the Hibernate object-relational mapping
framework[^3] as proposed by the Keycloak "Quickstarts" examples[^4].

## 2 Configuring and Installing the Storage Provider

### 2.1 Build the User Storage Provider

```shell
apt install maven
mvn package
```

To build for a specific version of Keycloak, for example `23.0.5`:

```shell
mvn -Dkeycloak.version=23.0.5 package
```

### 2.2 Create a Quarkus Properties File

At runtime, the storage provider is configured by a Quarkus properties file
that states the user database connection information. A template is included in
this project:

```shell
cp conf/quarkus.properties.example conf/quarkus.properties
```

Edit the properties file to reflect the user database connection; for example,
to connect to a PostGreSQL server reachable at "dbserver" on standard port
5432, open a database "users" as postgres user "user1" with password `secret`:

```properties
...
quarkus.datasource.user-store.db-kind=postgresql
quarkus.datasource.user-store.username=user1
quarkus.datasource.user-store.password=secret
quarkus.datasource.user-store.jdbc.url=jdbc:postgresql://dbserver/users
```

*Notes:*

This project features some hardcoded behavior in
[src/main/resources/META-INF/persistence.xml](src/main/resources/META-INF/persistence.xml),
because not all required Hibernate settings can currently be made using the
properties file. Consider the following Hibernate properties that are set in
`persistence.xml`:

* `hibernate.dialect`  
  This setting fixes the query language to a specific value (for example
  "PostgreSQLDialect") and is set in "persistence.xml" and not in
  "quarkus.properties" file for reasons that are currently intrinsic to
  Quarkus. Making this configurable exceeds the scope of this demonstration.
  Effectively, to support a different database, "user-storage-test.jar" has to
  be rebuilt.

* `hibernate.hbm2ddl.auto`  
  The proposed setting of "none" instructs hibernate not to check, drop, update
  or create any database table schemas; the database and tables are expected to
  be set up as described below in this document. This is the recommended
  setting for a production environment, where schema migrations are managed
  using external means.

* `hibernate.show_sql`  
  The proposed setting of "true" instructs hibernate to print generated SQL
  statements as it executes them, which is useful when debugging the database
  schema. In a production environment, this behavior might be unwanted for
  performance reasons.

* `jakarta.persistence.transactionType`  
  A setting of "JTA" is required to enable transactions across multiple XA
  datasources.

*Note:* There are other Hibernate settings, such as the fetching strategy,
which could be relevant in a production setting but are not demonstrated here. 

### 2.3 Optional: Change the Database Type to MariaDB

To use a different database driver:

* Change `hibernate.dialect` in
[src/main/resources/META-INF/persistence.xml](src/main/resources/META-INF/persistence.xml)

To use MariaDB, set the dialect to:

```xml
...
<properties>
    <property name="hibernate.dialect" value="org.hibernate.dialect.MariaDBDialect" />
...
```

* Change database driver and JDBC URL in
  [conf/quarkus.properties](conf/quarkus.properties.example)

To use a MariaDB database:

```properties
# Keycloak currently includes the "mariadb" JDBC driver
quarkus.datasource.user-store.db-kind=mariadb
...
quarkus.datasource.user-store.jdbc.url=jdbc:mariadb://db.example.test/userdb
```

### 2.4 Deploy the Provider and the Quarkus Properties File

```shell
scp target/user-storage-test.jar keycloak:/opt/keycloak/providers
scp conf/quarkus.properties keycloak:/opt/keycloak/conf
```

### 2.5 Perform a Keycloak Build

```shell
ssh keycloak /opt/keycloak/bin/kc.sh build
```
## 3 Setting up the Example User Database

### 3.1 (Optional) Apply the Example Database Setup

*Note:* The following sections describe the creation of a PostGreSQL user and
database and show the SQL statement for table definitions and example data.

If you do not want to follow the instructions step by step, you can also
apply an [example SQL file](sql/postgres/userdb.sql) containing all required
SQL statements:

Copy the SQL file to the PostGreSQL server:

```shell
scp sql/postgres/userdb.sql db.example.test:/tmp
```

On the PostGreSQL server:

```shell
su - postgres
psql < /tmp/userdb.sql
```

*Note:* You can [skip to section 3.5](#35-authenticate-using-kcadmsh) if you
have created the database using the SQL file "userdb.sql".

### 3.2 Create the Database

On the PostGreSQL server:

```shell
su - postgres
createuser userdb
psql
```

*Note:* The following actions are all executed in "psql".

Create the database and connect to it:

```sql
CREATE DATABASE userdb WITH ENCODING='UTF8' OWNER=userdb;
\c userdb
```

### 3.3 Create the Database Tables

Create the database table for user entities that will specify one identity per
row, containing user attributes supported by this storage provider:

```sql
CREATE TABLE public.users (
    id character varying(255) NOT NULL,
    createdtimestamp bigint,
    email character varying(255),
    firstname character varying(255),
    lastname character varying(255),
    password_hash character varying(255),
    username character varying(255)
);
ALTER TABLE ONLY public.users
    OWNER TO userdb;
ALTER TABLE ONLY public.users
    ADD CONSTRAINT u_pkey PRIMARY KEY (id);
```

Create the table of client role entities which will contain one client role per
row, stating the client ID and the name of the role:

```sql
CREATE TABLE public.client_roles (
    id character varying(255) NOT NULL,
    client character varying(255),
    role character varying(255)
);
ALTER TABLE ONLY public.client_roles
    OWNER TO userdb;
ALTER TABLE ONLY public.client_roles
    ADD CONSTRAINT c_pkey PRIMARY KEY (id);
```

Create the mapping table that will establish a many-to-many relation between
users and client roles:

```sql
CREATE TABLE public.users_to_client_roles (
    user_id character varying(255) NOT NULL,
    client_role_id character varying(255) NOT NULL
);
ALTER TABLE ONLY public.users_to_client_roles
    OWNER TO userdb;
ALTER TABLE ONLY public.users_to_client_roles
    ADD CONSTRAINT u2c_pkey
        PRIMARY KEY(user_id, client_role_id);
```

Add foreign-key relations between users, mappings and client roles:

```sql
ALTER TABLE ONLY public.users_to_client_roles
    ADD CONSTRAINT m2c_fkey
        FOREIGN KEY(client_role_id)
        REFERENCES public.client_roles(id);
ALTER TABLE ONLY public.users_to_client_roles
    ADD CONSTRAINT u2m2c_fkey
        FOREIGN KEY(user_id)
        REFERENCES public.users(id);
```

Create the table of realm role entities which will contain one realm role per
row, stating the name of the role:

```sql
CREATE TABLE public.realm_roles (
    id character varying(255) NOT NULL,
    role character varying(255)
);
ALTER TABLE ONLY public.realm_roles
    OWNER TO userdb;
ALTER TABLE ONLY public.realm_roles
    ADD CONSTRAINT r_pkey PRIMARY KEY (id);
```

Create the mapping table that will establish a many-to-many relation between
users and realm roles:

```sql
CREATE TABLE public.users_to_realm_roles (
    user_id character varying(255) NOT NULL,
    realm_role_id character varying(255) NOT NULL
);
ALTER TABLE ONLY public.users_to_realm_roles
    OWNER TO userdb;
ALTER TABLE ONLY public.users_to_realm_roles
    ADD CONSTRAINT u2r_pkey
        PRIMARY KEY(user_id, realm_role_id);
```

Add foreign-key relations between users, mappings and realm roles:

```sql
ALTER TABLE ONLY public.users_to_realm_roles
    ADD CONSTRAINT m2r_fkey
        FOREIGN KEY(realm_role_id)
        REFERENCES public.realm_roles(id);
ALTER TABLE ONLY public.users_to_realm_roles
    ADD CONSTRAINT u2m2r_fkey
        FOREIGN KEY(user_id)
        REFERENCES public.users(id);
```

### 3.4 Populate the Database with Test Data

The following record in the users table declares a user with preferred username
"mmustermann", password `B1Systems!` and example email and profile attributes:

```sql
INSERT INTO public.users VALUES (
    '1',
    CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS bigint),
    'm.mustermann@example.test',
    'Max',
    'Mustermann',
    '$6$xyz$C/vOVAshxi1VfgblFW220kcBCpZ7lihIohmNInE5M6wCQdxDaleG6LZzzGiRJ8sOWQHGwtYuz.8kcfZntV1OY/',
    'mmustermann'
);
```

*Notes:*
* The column "createdTimestamp" expects the datetime value as milliseconds
  since the epoch.
* The password `B1Systems!` has been hashed with the SHA-512 algorithm using a
  salt value of `xyz`.

The following record makes a client role "admin" of a client "testclient"
available:

```sql
INSERT INTO public.client_roles VALUES (
    '1', 'testclient', 'admin'
);
```

Define a mapping between user "mmustermann" and the client role
"testclient.admin":

```sql
INSERT INTO public.users_to_client_roles VALUES (
    '1', '1'
);
```

The following record makes a realm role "testrole" available:

```sql
INSERT INTO public.realm_roles VALUES (
    '1', 'testrole'
);
```

Define a mapping between user "mmustermann" and the realm role
"testrole":

```sql
INSERT INTO public.users_to_realm_roles VALUES (
    '1', '1'
);
```

### 3.5 Authenticate using "kcadm.sh"

*Note:* The remaining steps can also be done using the admin GUI of Keycloak.

To start using "kcadm.sh" for setting up a realm, attaching the provider and
creating the test client, first log in to the realm with a management account:

```shell
/opt/keycloak/bin/kcadm.sh config credentials \
    --server https://www.example.test/keycloak \
    --realm master \
    --user admin --password 'secret'
```

### 3.6 Add the User Storage Test Provider to the Realm

```shell
kcadm.sh create components \
    -r master \
    -s name="user-storage-test" \
    -s providerId="user-storage-test" \
    -s providerType="org.keycloak.storage.UserStorageProvider"
```

### 3.7 Create the Test Realm Role

Create a role "testrole" in realm "master":

```shell
kcadm.sh create roles \
    -r master \
    -s name=testrole
```

### 3.8 Create the Test Client

Create an OIDC client "testclient" in realm "master":

```
echo '{
    "clientId": "testclient"
}' | kcadm.sh create clients -r master -f -
```

This results in an ID value for the client:

```
Created new client with id '9d97b37c-77a9-4cd8-bf6e-2ee5f0028429'
```

### 3.9 Create the Test Client Role

Create a role "admin" in client "testclient" using the ID value from the previous command:

```
kcadm.sh create -r master \
    clients/9d97b37c-77a9-4cd8-bf6e-2ee5f0028429/roles \
    -s name=admin
```

## 4 Known Issues and Troubleshooting

### 4.1 How mappings to missing roles are handled

If a mapped entity in database table `client_roles` specifies a client or a
role name that does not exist in the federated realm, a log message of warning
level will be produced:

```
User mmustermann requests client role testclient.admin, but client
testclient does not exist; client role not assigned.
```

## Author, Copyright and License Information

* Author: Tilman Kranz &lt;[kranz@b1-systems.de](mailto:kranz@b1-systems.de)&gt;
* Copyright 2024 B1 Systems GmbH &lt;[info@b1-systems.de](mailto:info@b1-systems.de)&gt;

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at <http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.

## References

[^1]: <https://www.keycloak.org>
[^2]: <https://www.keycloak.org/docs/latest/server_development/index.html#_user-storage-spi>
[^3]: <https://hibernate.org/orm/>
[^4]: <https://github.com/keycloak/keycloak-quickstarts/tree/latest/extension/user-storage-jpa>
