-- userdb.sql - Example database for a postgresql datasource
-- See also <https://github.com/b1-systems/keycloak-user-storage-test>

CREATE USER userdb
    WITH ENCRYPTED PASSWORD 'userdb';
CREATE DATABASE userdb;
ALTER DATABASE userdb
    OWNER TO userdb;

\c userdb

-- Define tables

CREATE TABLE public.users (
    id character varying(255) NOT NULL,
    createdtimestamp bigint,
    email character varying(255),
    email_verified boolean DEFAULT false,
    firstname character varying(255),
    lastname character varying(255),
    password_hash character varying(255),
    username character varying(255)
);
ALTER TABLE ONLY public.users
    OWNER TO userdb;
ALTER TABLE ONLY public.users
    ADD CONSTRAINT u_pkey PRIMARY KEY (id);

CREATE TABLE public.client_roles (
    id character varying(255) NOT NULL,
    client character varying(255),
    role character varying(255)
);
ALTER TABLE ONLY public.client_roles
    OWNER TO userdb;
ALTER TABLE ONLY public.client_roles
    ADD CONSTRAINT c_pkey PRIMARY KEY (id);

CREATE TABLE public.users_to_client_roles (
    user_id character varying(255) NOT NULL,
    client_role_id character varying(255) NOT NULL
);
ALTER TABLE ONLY public.users_to_client_roles
    OWNER TO userdb;
ALTER TABLE ONLY public.users_to_client_roles
    ADD CONSTRAINT u2c_pkey
        PRIMARY KEY(user_id, client_role_id);
ALTER TABLE ONLY public.users_to_client_roles
    ADD CONSTRAINT m2c_fkey
        FOREIGN KEY(client_role_id)
        REFERENCES public.client_roles(id);
ALTER TABLE ONLY public.users_to_client_roles
    ADD CONSTRAINT u2m2c_fkey
        FOREIGN KEY(user_id)
        REFERENCES public.users(id);

CREATE TABLE public.realm_roles (
    id character varying(255) NOT NULL,
    role character varying(255)
);
ALTER TABLE ONLY public.realm_roles
    OWNER TO userdb;
ALTER TABLE ONLY public.realm_roles
    ADD CONSTRAINT r_pkey PRIMARY KEY (id);

CREATE TABLE public.users_to_realm_roles (
    user_id character varying(255) NOT NULL,
    realm_role_id character varying(255) NOT NULL
);
ALTER TABLE ONLY public.users_to_realm_roles
    OWNER TO userdb;
ALTER TABLE ONLY public.users_to_realm_roles
    ADD CONSTRAINT u2r_pkey
        PRIMARY KEY(user_id, realm_role_id);
ALTER TABLE ONLY public.users_to_realm_roles
    ADD CONSTRAINT m2r_fkey
        FOREIGN KEY(realm_role_id)
        REFERENCES public.realm_roles(id);
ALTER TABLE ONLY public.users_to_realm_roles
    ADD CONSTRAINT u2m2r_fkey
        FOREIGN KEY(user_id)
        REFERENCES public.users(id);

-- Add some example data

INSERT INTO public.users VALUES (
    '1',
    CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS bigint),
    'm.mustermann@example.test',
    false,
    'Max',
    'Mustermann',
    '$6$xyz$C/vOVAshxi1VfgblFW220kcBCpZ7lihIohmNInE5M6wCQdxDaleG6LZzzGiRJ8sOWQHGwtYuz.8kcfZntV1OY/',
    'mmustermann'
);
INSERT INTO public.client_roles VALUES (
    '1', 'testclient', 'admin'
);
INSERT INTO public.users_to_client_roles VALUES (
    '1', '1'
);
INSERT INTO public.realm_roles VALUES (
    '1', 'testrole'
);
INSERT INTO public.users_to_realm_roles VALUES (
    '1', '1'
);
