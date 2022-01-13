#!/bin/bash

# @TODO:
# Add transformation logic to the perf regression sample app
# Upgrade to hyperfoil-maven-plugin 0.18 to fix req/s and deps issue when released January 2022 ?
# Implement detection of regression (5% variation seems reasonable)
# Improve the staging support, today we need to hard code it pom.xml files
# Use an archetype instead of copying the perf regression sample app ?

# @NOTES:
# We should be able to build with camel-quarkus version >= 1.1.0 with current build approach
# We don't build using the quarkus-maven-plugin so that we can test against a SNAPSHOT or release candidate versions (don't need to wait for quarkus-platform release)

display_usage() {
  echo "This tool applies a performance test against a list of camel-quarkus versions. As such, it should be able to detect performance regressions."
  echo -e "Example: $0 2.4.0 2.5.0"
}

if [  $# -le 0 ]
then
  display_usage
  exit 1
fi

mkdir -p cq-versions-under-test
rm -fr cq-versions-under-test/*

pushd . >/dev/null

echo "Version	JVM(req/s)		Native(req/s)"
for cqVersion in "$@"
do
    # Generate Camel Quarkus performance regression sample for version ${cqVersion}
    cp -fr cq-perf-regression-sample-base "cq-versions-under-test/cq-perf-regression-sample-${cqVersion}" > /dev/null

    # Replace the pom parent version
	xmllint --shell "cq-versions-under-test/cq-perf-regression-sample-${cqVersion}/pom.xml" >/dev/null <<EOF
setns m=http://maven.apache.org/POM/4.0.0
cd //m:project/m:parent/m:version
set ${cqVersion}
save
EOF

    # Build and run test in JVM mode, then native mode
    pushd "cq-versions-under-test/cq-perf-regression-sample-${cqVersion}" > /dev/null
    mkdir -p target > /dev/null
    mvn integration-test > target/jvm-logs.txt
    mvn integration-test -Dnative -Dquarkus.native.container-build=true > target/native-logs.txt

	# Print the report line for this version
    #QUARKUS_JVM_NB_REQS=$(grep -Po "RunMojo] ([0-9]+) requests" "target/jvm-logs.txt" | sed -r 's/RunMojo] ([0-9]+) requests/\1/g')
    QUARKUS_JVM_NB_REQS=$(grep -Po "RunMojo] Requests/sec: ([0-9.,]+)" "target/jvm-logs.txt" | sed -r 's/RunMojo] Requests\/sec: ([0-9.,]+)/\1/g')
    #QUARKUS_NATIVE_NB_REQS=$(grep -Po "RunMojo] ([0-9]+) requests" "target/native-logs.txt" | sed -r 's/RunMojo] ([0-9]+) requests/\1/g')
    QUARKUS_NATIVE_NB_REQS=$(grep -Po "RunMojo] Requests/sec: ([0-9.,]+)" "target/native-logs.txt" | sed -r 's/RunMojo] Requests\/sec: ([0-9.,]+)/\1/g')
    echo "${cqVersion}	${QUARKUS_JVM_NB_REQS}		${QUARKUS_NATIVE_NB_REQS}"

    popd >/dev/null
done
