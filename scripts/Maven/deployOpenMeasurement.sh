#! /bin/bash

#################################
# Deploys the Life360 Ads Open Measurement SDK (com.life360:life360-ads-open-measurement-sdk)
# to the Maven Central Portal. Call this only when the vendored omsdk-android module
# has a new version (omSdkVersion in build.gradle).
#
# NOTE: deployPrebidMobile-life.sh already deploys omsdk as part of a full release.
# Use this script only for a standalone OM SDK release.
#################################

# Merge Script
if [ -d "Maven" ]; then
  cd Maven/
fi

set -euo pipefail

function echoX() {
  echo -e "LIFE360 DEPLOY-LOG: $@"
}

NAMESPACE="com.life360"
RELEASE_URL="https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
REPO_ID="central"

BASE_DIR="$PWD"
DEPLOY_DIR_ABSOLUTE="$BASE_DIR/filesToDeploy"

rm -r "$DEPLOY_DIR_ABSOLUTE" || true
mkdir "$DEPLOY_DIR_ABSOLUTE"

cd ..
# Read OMSDK_VERSION from the single source of truth in build.gradle
OMSDK_VERSION=""
omsdk_regex='omSdkVersion.*=.*"(.*)"'
while read -r line; do
  if [[ $line =~ $omsdk_regex ]]; then
    OMSDK_VERSION="${BASH_REMATCH[1]}"
  fi
done < "../build.gradle"
[[ -z "${OMSDK_VERSION}" ]] && { echoX "ERROR: could not read omSdkVersion from build.gradle"; exit 1; }
echoX "OMSDK v$OMSDK_VERSION"

bash ./buildPrebidMobile.sh
cp -r ../generated/* "$DEPLOY_DIR_ABSOLUTE" || true

####################################################
# HELPER FUNCTIONS
####################################################

# mavenDeploy [pom_path] [artifact_path] [sources_path] [javadoc_path] [deploy_url]
function mavenDeploy() {
  local POM="$1" FILE="$2" SOURCE="$3" JDOC="$4" URL="$5"

  MAVEN_GPG_PASSPHRASE="$GPG_PASSPHRASE" mvn -B gpg:sign-and-deploy-file \
    -DrepositoryId="${REPO_ID}" \
    -Durl="${URL}" \
    -DpomFile="${POM}" \
    -Dfile="${FILE}" \
    -Dsources="${SOURCE}" \
    -Djavadoc="${JDOC}" \
    -Dgpg.keyname="${GPG_KEYNAME}" \
    -Dgpg.executable=gpg \
    -Dgpg.homedir="$HOME/.gnupg" \
    "-DgpgArguments=--pinentry-mode loopback"
}

# replace_version_placeholder [pom_path] [revision]
function replace_version_placeholder() {
  local ORIGINAL_POM="$1"; local REVISION="$2";
  local MODIFIED_POM="$(dirname "$ORIGINAL_POM")/pom/pom.xml"
  mkdir -p "$(dirname "$ORIGINAL_POM")/pom"

  awk -v VER="$REVISION" -v OMSDK_VER="$REVISION" '
    {
      gsub(/<revision>[[:space:]]*[^<]*[[:space:]]*<\/revision>/, "<revision>" VER "</revision>")
      gsub(/\$\{omsdk\.version\}/, OMSDK_VER)
      print
    }
  ' "$ORIGINAL_POM" > "$MODIFIED_POM"
  echo "$MODIFIED_POM"
}

# load_maven_central_creds [serverId] [settingsPath]
function load_maven_central_creds() {
  local SERVER_ID="${1:-central}"
  local SETTINGS_PATH="${2:-$HOME/.m2/settings.xml}"

  if [[ ! -f "$SETTINGS_PATH" ]]; then
    echoX "Maven Central settings not found: $SETTINGS_PATH" >&2
    return 2
  fi

  if [[ -n "${CENTRAL_USERNAME:-}" && -n "${CENTRAL_PASSWORD:-}" ]]; then
    return 0
  fi

  local EXPORTS
  EXPORTS="$(
    python3 - "$SERVER_ID" "$SETTINGS_PATH" <<'PY'
import sys, os, xml.etree.ElementTree as ET, shlex
sid = sys.argv[1]
path = os.path.expanduser(sys.argv[2])

def warn(msg):
    sys.stderr.write(f"LIFE360 DEPLOY-LOG: {msg}\n")
    sys.stderr.flush()

try:
    tree = ET.parse(path)
except Exception as e:
    warn(f"Cannot read Maven Central settings at {path}: {e}")
    sys.exit(2)

root = tree.getroot()
for el in root.iter():
    if '}' in el.tag:
        el.tag = el.tag.split('}', 1)[1]

def text(elem, name):
    x = elem.find(name)
    return (x.text or '').strip() if x is not None else ''

servers = root.findall(".//servers/server")
matches = [s for s in servers if text(s, "id") == sid]
if not matches:
    warn(f'Server id "{sid}" not found in {path}')
    sys.exit(3)

srv = matches[0]
user = text(srv, "username")
pwd  = text(srv, "password")
if not user:
    warn(f'Username empty for server id "{sid}"')
    sys.exit(4)
if not pwd:
    warn(f'Password empty for server id "{sid}"')
    sys.exit(5)

print("export CENTRAL_USERNAME=" + shlex.quote(user))
print("export CENTRAL_PASSWORD=" + shlex.quote(pwd))
PY
  )" || return $?

  eval "$EXPORTS"
}

# finalizeUploadToPortal
function finalizeUploadToPortal() {
  echoX "Finalizing upload to Central Publisher Portal (namespace: ${NAMESPACE})"

  load_maven_central_creds
  : "${CENTRAL_USERNAME:?CENTRAL_USERNAME not set}" : "${CENTRAL_PASSWORD:?CENTRAL_PASSWORD not set}"

  local TOKEN
  TOKEN=$(printf "%s:%s" "$CENTRAL_USERNAME" "$CENTRAL_PASSWORD" | base64 | tr -d '\n')

  curl -sSf -X POST \
    "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/${NAMESPACE}?publishing_type=user_managed" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Length: 0"
}

####################################################
# DEPLOYMENT
####################################################

GPG_PASSPHRASE=""

function setupGPG() {
  if [[ -z "${GPG_KEYNAME:-}" ]]; then
    echoX "GPG_KEYNAME env var is not set. Take the 'sec xxxxxxx/GPG_KEYNAME' from the found keys below:" >&2
    gpg --list-secret-keys --keyid-format=long
    return 2
  fi

  read -r -s -p "GPG passphrase for '${GPG_KEYNAME}': " GPG_PASSPHRASE
  echo
  export GPG_TTY=${GPG_TTY:-$(tty || true)}
}

setupGPG

echoX "Deploying life360-ads-open-measurement-sdk on Maven..."
OMSDK_POM="$(replace_version_placeholder "${BASE_DIR}/PrebidMobile-open-measurement-pom.xml" "${OMSDK_VERSION}")"
mavenDeploy "$OMSDK_POM" \
  "$DEPLOY_DIR_ABSOLUTE/omsdk.jar" \
  "${BASE_DIR}/stub.jar" \
  "${BASE_DIR}/stub.jar" \
  "${RELEASE_URL}"

# Reset variables and temp data
unset GPG_PASSPHRASE
rm -rf pom

finalizeUploadToPortal

echoX "OM SDK uploaded to Central Portal as VALIDATED"
echoX "Open: https://central.sonatype.com/publishing/deployments to publish/drop"
echoX "End Script"
