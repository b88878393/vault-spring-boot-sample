#!/bin/sh
# ---------------------------------------------------------------------------------------------------------------------
# vault-init.sh - configure Vault for vault-spring-boot-sample.  Idempotent: safe to run any number of times.
#
#   * KV v2 engine at secret/ with DEMO secrets for the application
#   * database secrets engine: connection to PostgreSQL + role "app-readwrite" (dynamic credentials)
#   * transit engine with the encryption key "notes"
#   * policies from vault/policies/*.hcl
#   * AppRole "vault-spring-boot-sample"; its role_id and a fresh secret_id are written to .env (gitignored)
#
# Usage (from the repository root, after `docker compose up -d`):
#
#   ./scripts/vault-init.sh
#
# Uses a local `vault` CLI if present, otherwise runs the CLI inside the compose "vault" container.
# POSIX sh on purpose, so it also runs inside the (Alpine based) Vault image - the integration test does that.
#
# All settings can be overridden with environment variables, see the defaults below. The defaults match
# docker-compose.yml and are DEMO VALUES for a local dev-mode Vault only.
# ---------------------------------------------------------------------------------------------------------------------
set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(dirname -- "$SCRIPT_DIR")

: "${VAULT_ADDR:=http://127.0.0.1:8200}"
: "${VAULT_TOKEN:=dev-only-root-token}"          # DEMO root token of the dev-mode server (docker-compose.yml)
: "${APP_NAME:=vault-spring-boot-sample}"
: "${POLICY_DIR:=$PROJECT_DIR/vault/policies}"
: "${ENV_FILE:=$PROJECT_DIR/.env}"

# PostgreSQL as seen *from Vault* (inside the compose network the host name is "postgres").
: "${PG_HOST:=postgres}"
: "${PG_PORT:=5432}"
: "${PG_DATABASE:=appdb}"
: "${PG_VAULT_USER:=vault_admin}"
: "${PG_VAULT_PASSWORD:=vault-admin-demo-password}"  # DEMO value, see postgres/init.sql
: "${PG_SSLMODE:=disable}"                           # use verify-full in production

# Lifetimes. Lower DB_CREDS_* (e.g. 2m / 5m) to watch lease renewal and credential rotation happen.
: "${DB_CREDS_DEFAULT_TTL:=1h}"
: "${DB_CREDS_MAX_TTL:=24h}"
: "${APPROLE_TOKEN_TTL:=1h}"
: "${APPROLE_TOKEN_MAX_TTL:=4h}"
: "${APPROLE_SECRET_ID_TTL:=24h}"

# Set to "true" to overwrite existing demo secrets and the database connection config.
: "${FORCE:=false}"
# "auto" (default), "local" (vault CLI on PATH) or "compose" (docker compose exec into the vault service).
: "${VAULT_CLI_MODE:=auto}"

export VAULT_ADDR VAULT_TOKEN

log() { printf '==> %s\n' "$*" >&2; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

if [ "$VAULT_CLI_MODE" = auto ]; then
  if command -v vault >/dev/null 2>&1; then
    VAULT_CLI_MODE=local
  elif command -v docker >/dev/null 2>&1; then
    VAULT_CLI_MODE=compose
  else
    fail "neither the vault CLI nor docker is available"
  fi
fi

v() {
  if [ "$VAULT_CLI_MODE" = local ]; then
    vault "$@"
  else
    # VAULT_TOKEN is passed by name (value taken from this environment), so it does not show up in `ps`.
    docker compose -f "$PROJECT_DIR/docker-compose.yml" exec -T \
      -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN vault vault "$@"
  fi
}

mounted() { v secrets list | grep -q "^$1/ "; }
auth_enabled() { v auth list | grep -q "^$1/ "; }

retry() {
  _attempts=$1; shift
  _i=1
  until "$@"; do
    [ "$_i" -ge "$_attempts" ] && return 1
    _i=$((_i + 1)); sleep 2
  done
}

# --- 0. Wait for Vault ------------------------------------------------------------------------------------------------
log "Waiting for Vault at $VAULT_ADDR (cli mode: $VAULT_CLI_MODE)"
retry 30 v status >/dev/null 2>&1 || fail "Vault is not reachable/unsealed. Is 'docker compose up -d' running?"
v token lookup >/dev/null 2>&1 || fail "VAULT_TOKEN is not valid for $VAULT_ADDR"

# --- 1. KV v2 + demo secrets ------------------------------------------------------------------------------------------
if ! mounted secret; then
  log "Enabling KV v2 at secret/"
  v secrets enable -path=secret -version=2 kv >/dev/null
else
  # No-op for a KV v2 mount; upgrades a KV v1 mount.
  v kv enable-versioning secret/ >/dev/null
fi

if [ "$FORCE" = true ] || ! v kv get "secret/$APP_NAME" >/dev/null 2>&1; then
  log "Writing DEMO secrets to secret/$APP_NAME and secret/$APP_NAME/dev"
  v kv put "secret/$APP_NAME" \
    app.greeting="Hello from Vault KV v2 (demo value)" \
    app.max-note-length=2000 \
    partner.api-key="demo-partner-api-key-NOT-A-REAL-SECRET" >/dev/null
  v kv put "secret/$APP_NAME/dev" \
    app.greeting="Hello from Vault KV v2, dev profile (demo value)" >/dev/null
else
  log "secret/$APP_NAME already exists - keeping it (FORCE=true overwrites)"
fi

# --- 2. Database secrets engine ---------------------------------------------------------------------------------------
mounted database || { log "Enabling database secrets engine at database/"; v secrets enable database >/dev/null; }

if [ "$FORCE" = true ] || ! v read database/config/postgres >/dev/null 2>&1; then
  log "Configuring database/config/postgres -> $PG_HOST:$PG_PORT/$PG_DATABASE (waiting for PostgreSQL)"
  retry 30 v write database/config/postgres \
    plugin_name=postgresql-database-plugin \
    allowed_roles=app-readwrite \
    connection_url="postgresql://{{username}}:{{password}}@$PG_HOST:$PG_PORT/$PG_DATABASE?sslmode=$PG_SSLMODE" \
    username="$PG_VAULT_USER" \
    password="$PG_VAULT_PASSWORD" \
    password_authentication=scram-sha-256 >/dev/null \
    || fail "could not configure the PostgreSQL connection (is the postgres container healthy?)"
else
  # Not rewritten on purpose: after `vault write -f database/rotate-root/postgres` only Vault knows the password.
  log "database/config/postgres already exists - keeping it (FORCE=true overwrites)"
fi

log "Writing database role app-readwrite (default_ttl=$DB_CREDS_DEFAULT_TTL, max_ttl=$DB_CREDS_MAX_TTL)"
v write database/roles/app-readwrite \
  db_name=postgres \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}' IN ROLE app_owner;" \
  revocation_statements="DROP ROLE IF EXISTS \"{{name}}\";" \
  default_ttl="$DB_CREDS_DEFAULT_TTL" \
  max_ttl="$DB_CREDS_MAX_TTL" >/dev/null

# --- 3. Transit -------------------------------------------------------------------------------------------------------
mounted transit || { log "Enabling transit secrets engine at transit/"; v secrets enable transit >/dev/null; }
if ! v read transit/keys/notes >/dev/null 2>&1; then
  log "Creating transit key 'notes' (aes256-gcm96, not exportable)"
  v write -f transit/keys/notes type=aes256-gcm96 >/dev/null
fi

# --- 4. Policies ------------------------------------------------------------------------------------------------------
[ -f "$POLICY_DIR/$APP_NAME.hcl" ] || fail "policy file $POLICY_DIR/$APP_NAME.hcl not found"
log "Writing policies $APP_NAME and $APP_NAME-operator"
v policy write "$APP_NAME" - < "$POLICY_DIR/$APP_NAME.hcl" >/dev/null
v policy write "$APP_NAME-operator" - < "$POLICY_DIR/operator.hcl" >/dev/null

# --- 5. AppRole -------------------------------------------------------------------------------------------------------
auth_enabled approle || { log "Enabling AppRole auth at approle/"; v auth enable approle >/dev/null; }
log "Writing AppRole $APP_NAME"
v write "auth/approle/role/$APP_NAME" \
  token_policies="$APP_NAME" \
  token_ttl="$APPROLE_TOKEN_TTL" \
  token_max_ttl="$APPROLE_TOKEN_MAX_TTL" \
  secret_id_ttl="$APPROLE_SECRET_ID_TTL" \
  secret_id_num_uses=0 >/dev/null

ROLE_ID=$(v read -field=role_id "auth/approle/role/$APP_NAME/role-id")
SECRET_ID=$(v write -f -field=secret_id "auth/approle/role/$APP_NAME/secret-id")
[ -n "$ROLE_ID" ] && [ -n "$SECRET_ID" ] || fail "could not obtain role_id/secret_id"

# --- 6. .env ----------------------------------------------------------------------------------------------------------
# Keep unrelated lines of an existing .env, replace only the AppRole credentials. File mode 600.
umask 077
TMP_FILE="$ENV_FILE.tmp.$$"
{
  if [ -f "$ENV_FILE" ]; then
    grep -v -e '^VAULT_ROLE_ID=' -e '^VAULT_SECRET_ID=' -e '^# AppRole credentials' "$ENV_FILE" || true
  else
    echo "# Local environment for docker compose / the app. Generated by scripts/vault-init.sh - DO NOT COMMIT."
  fi
  echo "# AppRole credentials written by scripts/vault-init.sh at $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "VAULT_ROLE_ID=$ROLE_ID"
  echo "VAULT_SECRET_ID=$SECRET_ID"
} > "$TMP_FILE"
mv "$TMP_FILE" "$ENV_FILE"

log "Done. AppRole role_id=$ROLE_ID; a new secret_id (valid $APPROLE_SECRET_ID_TTL) was written to $ENV_FILE"
log "Next: docker compose --profile app up -d --build    (or: set -a; . ./.env; set +a; ./mvnw spring-boot:run)"
