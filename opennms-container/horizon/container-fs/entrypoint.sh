#!/usr/bin/env bash
# =====================================================================
# Build script running OpenNMS in Docker environment
#
# Source: https://github.com/opennms-forge/docker-horizon-core-web
# Web: https://www.opennms.org
#
# =====================================================================

# Cause false/positives
# shellcheck disable=SC2086

set -euo pipefail
trap 's=$?; echo >&2 "$0: Error on line "$LINENO": $BASH_COMMAND"; exit $s' ERR

umask 002
export OPENNMS_HOME="/usr/share/opennms"

OPENNMS_OVERLAY="/opt/opennms-overlay"
OPENNMS_OVERLAY_ETC="/opt/opennms-etc-overlay"
OPENNMS_OVERLAY_JETTY_WEBINF="/opt/opennms-jetty-webinf-overlay"

# Error codes
E_ILLEGAL_ARGS=126
E_INIT_CONFIG=127

MYID="$(id -u)"
MYUSER="$(getent passwd "${MYID}" | cut -d: -f1 || true)"

export RUNAS="${MYUSER}"

if [ "$MYID" -eq 0 ] && [ -n "${MYUSER}" ]; then
  if ! grep -Fxq "RUNAS=${MYUSER}" "${OPENNMS_HOME}/etc/opennms.conf"; then
      echo "RUNAS=${MYUSER}" >> "${OPENNMS_HOME}/etc/opennms.conf"
  fi
  chown "$MYUSER" "${OPENNMS_HOME}/etc/opennms.conf"
fi

# Help function used in error messages and -h option
usage() {
  echo ""
  echo "Docker entry script for OpenNMS service container"
  echo ""
  echo "Overlay Config file:"
  echo "If you want to overwrite the default configuration with your custom config, you can use an overlay config"
  echo "folder in which needs to be mounted to ${OPENNMS_OVERLAY_ETC}."
  echo "Every file in this folder is overwriting the default configuration file in ${OPENNMS_HOME}/etc."
  echo ""
  echo "-f: Start OpenNMS in foreground with existing data and configuration."
  echo "-h: Show this help."
  echo "-i: Initialize or update database and configuration files and do *NOT* start."
  echo "-s: Initialize or update database and configuration files and start OpenNMS."
  echo "-S: Same as -s, with quiet initialization on success and OpenNMS progress bar."
  echo "-t: Run the config-tester, e.g -t -h to show help and available options."
  echo ""
}

initOrUpdate() {
  if [[ -f "${OPENNMS_HOME}"/etc/configured ]]; then
    echo "System is already configured. Enforce init or update by delete the ${OPENNMS_HOME}/etc/configured file."
  else
    echo "Find and set Java environment for running OpenNMS in ${OPENNMS_HOME}/etc/java.conf."
    "${OPENNMS_HOME}"/bin/runjava -s || return ${E_INIT_CONFIG}

    echo "Run OpenNMS install command to initialize or upgrade the database schema and configurations."
    ${JAVA_HOME}/bin/java -Dopennms.home="${OPENNMS_HOME}" -Dlog4j.configurationFile="${OPENNMS_HOME}"/etc/log4j2-tools.xml -cp "${OPENNMS_HOME}/lib/opennms_bootstrap.jar" org.opennms.bootstrap.InstallerBootstrap "${@}" || return ${E_INIT_CONFIG}

    # If Newts is used initialize the keyspace with a given REPLICATION_FACTOR which defaults to 1 if unset
    if [[ ! -v OPENNMS_TIMESERIES_STRATEGY ]]; then
      echo "The time series strategy OPENNMS_TIMESERIES_STRATEGY is not set, so skip Newts keyspace initialisation. When unset, defaults to 'rrd' to use RRDTool."
    elif  [[ "${OPENNMS_TIMESERIES_STRATEGY}" == "newts" ]]; then
      ${JAVA_HOME}/bin/java -Dopennms.manager.class="org.opennms.netmgt.newts.cli.Newts" -Dopennms.home="${OPENNMS_HOME}" -Dlog4j.configurationFile="${OPENNMS_HOME}"/etc/log4j2-tools.xml -jar ${OPENNMS_HOME}/lib/opennms_bootstrap.jar init -r ${REPLICATION_FACTOR-1} || return ${E_INIT_CONFIG}
    else
      echo "The time series strategy ${OPENNMS_TIMESERIES_STRATEGY} is selected, so skip Newts keyspace initialisation."
    fi
  fi
}

configTester() {
  echo "Run config tester to validate existing configuration files."
  ${JAVA_HOME}/bin/java -Dopennms.manager.class="org.opennms.netmgt.config.tester.ConfigTester" -Dopennms.home="${OPENNMS_HOME}" -Dlog4j.configurationFile="${OPENNMS_HOME}"/etc/log4j2-tools.xml -jar ${OPENNMS_HOME}/lib/opennms_bootstrap.jar "${@}" || return ${E_INIT_CONFIG}
}

processConfdTemplates() {
  echo "Processing confd templates using /etc/confd/confd.toml"
  confd -onetime
}

# Initialize database and configure Karaf
initConfigWhenEmpty() {
  if [ ! -d ${OPENNMS_HOME} ]; then
    echo "OpenNMS home directory doesn't exist in ${OPENNMS_HOME}."
    return ${E_ILLEGAL_ARGS}
  fi

  if [ ! "$(ls --ignore .git --ignore .gitignore -A ${OPENNMS_HOME}/etc)"  ]; then
    echo "No existing configuration in ${OPENNMS_HOME}/etc found. Initialize from etc-pristine."
    cp -r ${OPENNMS_HOME}/share/etc-pristine/* ${OPENNMS_HOME}/etc/ || return ${E_INIT_CONFIG}
  fi

  if [[ ! -d /opennms-data/mibs ]]; then
    echo "Mibs data directory does not exist, create directory in /opennms-data/mibs"
    mkdir /opennms-data/mibs || return ${E_INIT_CONFIG}
  else
    echo "Use existing Mibs data directory."
  fi

  if [[ ! -d /opennms-data/reports ]]; then
    echo "Reports data directory does not exist, create directory in /opennms-data/reports"
    mkdir /opennms-data/reports || return ${E_INIT_CONFIG}
  else
    echo "Use existing Reports data directory."
  fi

  if [[ ! -d /opennms-data/rrd ]]; then
    echo "RRD data directory does not exist, create directory in /opennms-data/rrd"
    mkdir /opennms-data/rrd || return ${E_INIT_CONFIG}
  else
    echo "Use existing RRD data directory."
  fi
}

applyOverlayConfig() {
  # Overlay relative to the root of the install dir
  if [ -d "${OPENNMS_OVERLAY}" ] && [ -n "$(ls -A ${OPENNMS_OVERLAY})" ]; then
    echo "Apply custom configuration from ${OPENNMS_OVERLAY}."
    # Use rsync so that we can overlay files into directories that are symlinked
    rsync -K -rl ${OPENNMS_OVERLAY}/* ${OPENNMS_HOME}/ || return ${E_INIT_CONFIG}
  else
    echo "No custom config found in ${OPENNMS_OVERLAY}. Use default configuration."
  fi

  # Overlay etc specific config
  if [ -d "${OPENNMS_OVERLAY_ETC}" ] && [ -n "$(ls -A ${OPENNMS_OVERLAY_ETC})" ]; then
    echo "Apply custom etc configuration from ${OPENNMS_OVERLAY_ETC}."
    cp -r ${OPENNMS_OVERLAY_ETC}/* ${OPENNMS_HOME}/etc || return ${E_INIT_CONFIG}
  else
    echo "No custom config found in ${OPENNMS_OVERLAY_ETC}. Use default configuration."
  fi

  # Overlay jetty specific config
  if [ -d "${OPENNMS_OVERLAY_JETTY_WEBINF}" ] && [ -n "$(ls -A ${OPENNMS_OVERLAY_JETTY_WEBINF})" ]; then
    echo "Apply custom Jetty WEB-INF configuration from ${OPENNMS_OVERLAY_JETTY_WEBINF}."
    cp -r ${OPENNMS_OVERLAY_JETTY_WEBINF}/* ${OPENNMS_HOME}/jetty-webapps/opennms/WEB-INF || return ${E_INIT_CONFIG}
  else
    echo "No custom Jetty WEB-INF config found in ${OPENNMS_OVERLAY_JETTY_WEBINF}. Use default configuration."
  fi
}

# Start opennms in foreground
start() {
  local OPENNMS_JAVA_OPTS="--add-modules=java.base,java.compiler,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.xml,jdk.attach,jdk.httpserver,jdk.jdi,jdk.sctp,jdk.security.auth,jdk.xml.dom \
  -Dorg.apache.jasper.compiler.disablejsr199=true
  -Dopennms.home=${OPENNMS_HOME}
  -Dopennms.pidfile=${OPENNMS_HOME}/logs/opennms.pid
  -XX:+HeapDumpOnOutOfMemoryError
  -Dcom.sun.management.jmxremote.authenticate=true
  -Dcom.sun.management.jmxremote.login.config=opennms
  -Dcom.sun.management.jmxremote.access.file=${OPENNMS_HOME}/etc/jmxremote.access
  -DisThreadContextMapInheritable=true
  -Djdk.attach.allowAttachSelf=true
  -Dgroovy.use.classvalue=true
  -Djava.io.tmpdir=${OPENNMS_HOME}/data/tmp
  -Djava.locale.providers=CLDR,COMPAT
  -XX:+StartAttachListener"
  exec ${JAVA_HOME}/bin/java ${OPENNMS_JAVA_OPTS} ${JAVA_OPTS} -jar ${OPENNMS_HOME}/lib/opennms_bootstrap.jar start
}

quietlyRun() {
  local description="$1"; shift

  local log="logs/$1.log"

  echo "Initialization: ${description}: starting"

  if "$@" &>"${log}"; then
    ret=0
  else
    ret=$?
  fi

  if [ $ret -eq 0 ]; then
    echo "Initialization: ${description}: done (log: ${log})"
  else
    echo "Initialization: ${description}: ERROR: see output below (see log in ${log})" >&2
    cat "${log}" >&2
    exit $ret
  fi
}

# Evaluate arguments for build script.
if [[ "${#}" == 0 ]]; then
  usage
  exit ${E_ILLEGAL_ARGS}
fi

# Evaluate arguments for build script.
while getopts "fhisSt" flag; do
  case ${flag} in
    f)
      processConfdTemplates
      applyOverlayConfig
      configTester -a
      start
      exit
      ;;
    h)
      usage
      exit
      ;;
    i)
      initConfigWhenEmpty
      processConfdTemplates
      applyOverlayConfig
      configTester -a
      initOrUpdate -dis
      exit
      ;;
    s)
      initConfigWhenEmpty
      processConfdTemplates
      applyOverlayConfig
      configTester -a
      initOrUpdate -dis
      start
      exit
      ;;
    S)
      quietlyRun "initialize config" initConfigWhenEmpty
      quietlyRun "process confd templates" processConfdTemplates
      quietlyRun "apply overlay config" applyOverlayConfig
      quietlyRun "test configuration" configTester -a
      quietlyRun "run installer" initOrUpdate -dis
      tail -F logs/progressbar.log &
      start &> logs/output.log
      exit
      ;;
    t)
      shift $((OPTIND - 1))
      configTester "${@}"
      exit
      ;;
    *)
      usage
      exit ${E_ILLEGAL_ARGS}
      ;;
  esac
done

# Strip of all remaining arguments
shift $((OPTIND - 1));

# Check if there are remaining arguments
if [[ "${#}" -gt 0 ]]; then
  echo "Error: To many arguments: ${*}."
  usage
  exit ${E_ILLEGAL_ARGS}
fi
