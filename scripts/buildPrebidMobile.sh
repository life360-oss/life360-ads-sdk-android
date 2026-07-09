#! /bin/bash
# This script builds the  Life360 Ads SDK in the following steps:
# It will ask you the version you're releasing
# Check if it's the same as the one in the project's build.gradle
# Package releases
# End

######################
# Helper Methods
######################

# Merge Script
if [ -d "scripts" ]; then
  cd scripts/
fi

set -e

cd ..
echo -e "$PWD"

# Setup some constants for use later on.

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

function echoX() {
  echo -e "Life360 Ads SDK BUILDLOG: $@"
}

die() {
  echoX "$@" 1>&2
  echoX "End Script"
  exit 1
}

######################
# Build Settings
######################

# file paths
BASEDIR="$PWD"
OUTDIR=$BASEDIR/generated
LOGPATH=$OUTDIR/logs
FAT_PATH=$OUTDIR/fat
AARPATH=build/outputs/aar
BUILD_LIBS_PATH=build/libs
TEMPDIR=$OUTDIR/temp
LIBDIR=$BASEDIR

echoX "$BASEDIR"

# set the default release version to what's in the project's build.gradle file
RELEASE_VERSION=""
regex="prebidSdkVersionName.*=.*\"(.*)\""
while read -r line; do
  if [[ $line =~ $regex ]]; then
    RELEASE_VERSION=${BASH_REMATCH[1]}
  fi
done <$LIBDIR/build.gradle

omsdk_regex='omSdkVersion.*=.*"(.*)"'
while read -r line; do
  if [[ $line =~ $omsdk_regex ]]; then
    OMSDK_VERSION=${BASH_REMATCH[1]}
    echoX "OMSDK v$OMSDK_VERSION"
  fi
done <$LIBDIR/build.gradle

echoX "Start building  Life360 Ads SDK version $RELEASE_VERSION"

###########################
# Prepare
###########################
echoX "Clean directories"
rm -rf $OUTDIR
mkdir $OUTDIR
mkdir $LOGPATH
rm -rf $TEMPDIR
cd $LIBDIR
./gradlew -i --no-daemon clean >$LOGPATH/clean.log 2>&1

###########################
# Generate modules
###########################

modules=(
  "PrebidMobile"
  "PrebidMobile-core"
  "PrebidMobile-gamEventHandlers"
  "PrebidMobile-admobAdapters"
  "PrebidMobile-maxAdapters"
)

projectPaths=(
  "$BASEDIR/PrebidMobile"
  "$BASEDIR/PrebidMobile/PrebidMobile-core"
  "$BASEDIR/PrebidMobile/PrebidMobile-gamEventHandlers"
  "$BASEDIR/PrebidMobile/PrebidMobile-admobAdapters"
  "$BASEDIR/PrebidMobile/PrebidMobile-maxAdapters"
)

mkdir "$OUTDIR/aar"
for n in ${!modules[@]}; do

  echo -e "\n"
  # Derive the public output name: PrebidMobile[-suffix] → Life360AdsSDK[-suffix]
  OUTPUT_NAME="${modules[$n]/PrebidMobile/Life360AdsSDK}"
  echoX "Assembling and repackaging ${OUTPUT_NAME}"
  cd $LIBDIR
  # Build the release AAR and run JarJar to relocate org.prebid.mobile.** → com.life360.ads.**
  (./gradlew -i --no-daemon ${modules[$n]}:repackageReleaseAar >$LOGPATH/build.log 2>&1 || die "Build failed, check log in $LOGPATH/build.log")

  if [ "$1" != "-nojar" ]; then
    # Make folder generated/temp/output (remove any leftovers from a failed prior run)
    echoX "Packaging ${OUTPUT_NAME}"
    rm -rf $TEMPDIR
    mkdir $TEMPDIR
    cd $TEMPDIR
    mkdir output

    AARPATH_ABSOLUTE="${projectPaths[$n]}/$AARPATH"

    cd $AARPATH_ABSOLUTE
    # Copy repackaged AAR under the public output name
    cp ${modules[$n]}-release-repackaged.aar $OUTDIR/aar/${OUTPUT_NAME}-release.aar
    unzip -q -o ${modules[$n]}-release-repackaged.aar
    cd $TEMPDIR/output

    # Extracting the Contents of a JAR File
    jar xf $AARPATH_ABSOLUTE/classes.jar
    rm $AARPATH_ABSOLUTE/classes.jar

    # Handle ProGuard rules from .aar into .jar
    # rename proguard.txt to proguard.pro
    mv $AARPATH_ABSOLUTE/proguard.{txt,pro}
    mkdir -p $AARPATH_ABSOLUTE/META-INF
    mkdir $AARPATH_ABSOLUTE/META-INF/proguard
    mv $AARPATH_ABSOLUTE/proguard.pro $AARPATH_ABSOLUTE/META-INF/proguard
    # move META-INF into a result direcotory
    # mv $AARPATH_ABSOLUTE/META-INF $TEMPDIR/output

    mkdir -p $TEMPDIR/output/META-INF
    cp -r $AARPATH_ABSOLUTE/META-INF/. $TEMPDIR/output/META-INF/
    # rm -rf $AARPATH_ABSOLUTE/META-INF

    rm -rf $TEMPDIR/output/META-INF/com

    # Creating a JAR File (output named Life360AdsSDK-*)
    # After repackaging, all org.prebid.mobile.* classes are now com.life360.ads.*
    # so we glob com* instead of org*. Life360AdsSDK (wrapper) has no classes of its own.
    if [ "${modules[$n]}" == "PrebidMobile" ]; then
      jar cf ${OUTPUT_NAME}.jar META-INF*
    else
      jar cf ${OUTPUT_NAME}.jar com* META-INF*
    fi

    # move jar into result directory
    mv ${OUTPUT_NAME}.jar $OUTDIR

    cd $LIBDIR

     # Javadoc
     echoX "Preparing ${modules[$n]} Javadoc"
     ./gradlew -i --no-daemon ${modules[$n]}:javadocJar >$LOGPATH/javadoc.log 2>&1 || die "Build Javadoc failed, check log in $LOGPATH/javadoc.log"

    # Sources
    echoX "Preparing ${OUTPUT_NAME} sources"
    ./gradlew -i --no-daemon ${modules[$n]}:sourcesJar >$LOGPATH/sources.log 2>&1 || die "Build Sources failed, check log in $LOGPATH/sources.log"

    # copy sources and javadoc into result directory, then rename from PrebidMobile-* to Life360AdsSDK-*
    BUILD_LIBS_PATH_ABSOLUTE="${projectPaths[$n]}/$BUILD_LIBS_PATH"
    cp -a $BUILD_LIBS_PATH_ABSOLUTE/. $OUTDIR/
    for f in "$OUTDIR"/${modules[$n]}-*.jar; do
      [ -f "$f" ] && mv "$f" "${f/${modules[$n]}/${OUTPUT_NAME}}"
    done
    # clean tmp dir
    rm -r $TEMPDIR
  fi
done


if [ "$1" != "-nojar" ]; then
  ### omsdk
  echo -e "\n"
  echoX "Assembling omsdk"

  mkdir $TEMPDIR
  cd $TEMPDIR
  mkdir output
  cd output
  cp -a "$BASEDIR/PrebidMobile/omsdk-android/omsdk-android-${OMSDK_VERSION}.aar" "$TEMPDIR/output"
  unzip -q -o omsdk-android-${OMSDK_VERSION}.aar
  # Delete all files instead classes.jar
  find . ! -name 'classes.jar' -type f -exec rm -f {} +
  unzip -q -o classes.jar
  rm classes.jar

  jar cf omsdk.jar com*
  mv omsdk.jar $OUTDIR
  cd $LIBDIR
  rm -r $TEMPDIR
fi

###########################
# Generate POM files
###########################
echoX "Generating POM files"
POM_TEMPLATE_DIR="$BASEDIR/scripts/Maven"
POM_OUTDIR="$OUTDIR/pom"
mkdir "$POM_OUTDIR"

for module in "${modules[@]}"; do
  # Output POM uses the public Life360AdsSDK-* name
  POM_OUTPUT_NAME="${module/PrebidMobile/Life360AdsSDK}"
  TEMPLATE="$POM_TEMPLATE_DIR/${module}-pom.xml"
  if [ -f "$TEMPLATE" ]; then
    awk -v VER="$RELEASE_VERSION" -v OMSDK_VER="$OMSDK_VERSION" '
      { gsub(/<revision>[^<]*<\/revision>/, "<revision>" VER "<\/revision>")
        gsub(/<version>[[:space:]]*\$\{revision\}[[:space:]]*<\/version>/, "<version>" VER "<\/version>")
        gsub(/<version>[[:space:]]*\$\{project\.version\}[[:space:]]*<\/version>/, "<version>" VER "<\/version>")
        gsub(/\$\{omsdk\.version\}/, OMSDK_VER)
        print }
    ' "$TEMPLATE" > "$POM_OUTDIR/${POM_OUTPUT_NAME}-${RELEASE_VERSION}.pom"
    echoX "  Generated $POM_OUTDIR/${POM_OUTPUT_NAME}-${RELEASE_VERSION}.pom"
  else
    echoX "  WARNING: No POM template found for ${module} at $TEMPLATE"
  fi
done

### omsdk POM
OMSDK_TEMPLATE="$POM_TEMPLATE_DIR/PrebidMobile-open-measurement-pom.xml"
if [ -f "$OMSDK_TEMPLATE" ]; then
  awk -v OMSDK_VER="$OMSDK_VERSION" '
    { gsub(/\$\{omsdk\.version\}/, OMSDK_VER)
      print }
  ' "$OMSDK_TEMPLATE" > "$POM_OUTDIR/Life360AdsSDK-omsdk-${OMSDK_VERSION}.pom"
  echoX "  Generated $POM_OUTDIR/Life360AdsSDK-omsdk-${OMSDK_VERSION}.pom"
else
  echoX "  WARNING: No POM template found for omsdk at $OMSDK_TEMPLATE"
fi

#######
# End
#######
echoX "Please find  Life360 Ads SDK artifacts in $OUTDIR"
echo -e "\n${GREEN}Done!${NC} \n"
