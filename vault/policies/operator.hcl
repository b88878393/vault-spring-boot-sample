# ---------------------------------------------------------------------------------------------------------------
# Example policy for a human operator / CI job that administers the sample WITHOUT a root token:
# update the application's KV secrets, rotate the Transit key and the DB root credential, and issue new AppRole
# secret IDs. Create a token with:  vault token create -policy=vault-spring-boot-sample-operator -ttl=1h
# ---------------------------------------------------------------------------------------------------------------

# Manage the application's KV v2 secrets (data, versions and metadata).
path "secret/data/vault-spring-boot-sample" {
  capabilities = ["create", "read", "update"]
}

path "secret/data/vault-spring-boot-sample/*" {
  capabilities = ["create", "read", "update"]
}

path "secret/metadata/vault-spring-boot-sample" {
  capabilities = ["read", "list"]
}

path "secret/metadata/vault-spring-boot-sample/*" {
  capabilities = ["read", "list"]
}

# Rotate the Transit key used for notes and inspect its versions.
path "transit/keys/notes" {
  capabilities = ["read"]
}

path "transit/keys/notes/rotate" {
  capabilities = ["update"]
}

# Rotate the password of the vault_admin PostgreSQL user so that only Vault knows it.
path "database/rotate-root/postgres" {
  capabilities = ["update"]
}

# Issue new secret IDs for the application's AppRole (and read its role ID).
path "auth/approle/role/vault-spring-boot-sample/role-id" {
  capabilities = ["read"]
}

path "auth/approle/role/vault-spring-boot-sample/secret-id" {
  capabilities = ["update"]
}
