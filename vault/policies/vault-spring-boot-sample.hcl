# ---------------------------------------------------------------------------------------------------------------
# Policy for the vault-spring-boot-sample application (attached to the AppRole "vault-spring-boot-sample").
# Least privilege: read its own KV secrets, obtain its own DB credentials, use (not manage) one Transit key.
# Token self-management (renew-self, lookup-self, revoke-self) comes from Vault's built-in "default" policy.
# ---------------------------------------------------------------------------------------------------------------

# (1) KV v2 static secrets. Spring Cloud Vault reads <app-name>, <app-name>/<profile> and the shared
# "application" context. KV v2 data lives under the "data/" prefix of the mount.
path "secret/data/vault-spring-boot-sample" {
  capabilities = ["read"]
}

path "secret/data/vault-spring-boot-sample/*" {
  capabilities = ["read"]
}

path "secret/data/application" {
  capabilities = ["read"]
}

# (3) Dynamic PostgreSQL credentials for exactly one role.
path "database/creds/app-readwrite" {
  capabilities = ["read"]
}

# Lease management for the dynamic credentials (renew in the background, revoke on shutdown).
path "sys/leases/renew" {
  capabilities = ["update"]
}

path "sys/leases/revoke" {
  capabilities = ["update"]
}

# (4) Transit: encrypt, decrypt and rewrap with the "notes" key. The application cannot read, export,
# rotate or delete the key - rotation is an operator task (see operator.hcl).
path "transit/encrypt/notes" {
  capabilities = ["update"]
}

path "transit/decrypt/notes" {
  capabilities = ["update"]
}

path "transit/rewrap/notes" {
  capabilities = ["update"]
}
