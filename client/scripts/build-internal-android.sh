#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
KEYSTORE_PATH="$PROJECT_DIR/.signing/mapmory-internal-upload.jks"
KEYCHAIN_ACCOUNT="mapmory-internal-upload"
KEYCHAIN_SERVICE="com.mapmory.android.internal.upload-key"
KEY_ALIAS="mapmory-internal-upload"

if [ ! -f "$KEYSTORE_PATH" ]; then
    echo "Missing internal upload keystore: $KEYSTORE_PATH" >&2
    exit 1
fi

KEYSTORE_PASSWORD=$(security find-generic-password \
    -a "$KEYCHAIN_ACCOUNT" \
    -s "$KEYCHAIN_SERVICE" \
    -w)

MAPMORY_INTERNAL_STORE_FILE="$KEYSTORE_PATH" \
MAPMORY_INTERNAL_STORE_PASSWORD="$KEYSTORE_PASSWORD" \
MAPMORY_INTERNAL_KEY_ALIAS="$KEY_ALIAS" \
MAPMORY_INTERNAL_KEY_PASSWORD="$KEYSTORE_PASSWORD" \
    "$PROJECT_DIR/gradlew" \
        :androidApp:bundleInternal \
        :androidApp:assembleInternal \
        --no-daemon

unset KEYSTORE_PASSWORD

echo "Signed bundle: $PROJECT_DIR/androidApp/build/outputs/bundle/internal/androidApp-internal.aab"
echo "Signed APK: $PROJECT_DIR/androidApp/build/outputs/apk/internal/androidApp-internal.apk"
