#!/bin/sh
set -eu

# Production device builds only. Debug/Internal and simulator builds do not upload.
if [ "${CONFIGURATION:-}" != "Release" ] || [ "${PLATFORM_NAME:-}" != "iphoneos" ]; then
    echo "Crashlytics symbols: skipped for ${CONFIGURATION:-unknown}/${PLATFORM_NAME:-unknown}"
    exit 0
fi

crashlytics_run="${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/run"
if [ ! -f "$crashlytics_run" ]; then
    echo "error: Firebase Crashlytics run script is missing. Resolve Swift packages before building."
    exit 1
fi

# Firebase associates these symbols with the built app's GoogleService-Info.plist.
/bin/sh "$crashlytics_run"
