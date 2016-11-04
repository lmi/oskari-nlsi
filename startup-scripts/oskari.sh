#! /bin/bash
#
#	2016-10-11 Hafliði Sigtryggur Magnússon
#
#	Start Oskari, script is run through systemctl
#
#
export COMPONENT=`basename $0|sed 's/\.sh$//g'`
export RUN_USER=oskari
export PIDFILE=/var/run/${RUN_USER}/${COMPONENT}.pid
export LOGFILE=/var/log/${RUN_USER}/${COMPONENT}/std.log

export JAVA_HOME=/opt/jdk
export JETTY_HOME=/opt/${RUN_USER}/${COMPONENT}
export JAVA_EXT_LIB=/usr/local/share/java

export PATH=$JAVA_HOME/bin:$PATH
export CLASSPATH=$JAVA_EXT_LIB:$CLASSPATH
#export GEOSERVER_DATA_DIR=/opt/geoserver_data


case "$1" in
start)
	if test "`id -un`" != "$RUN_USER"
	then
		echo "Only \"`id $RUN_USER`\" allowed to start oskari"
		exit 1
	fi

	cd "$JETTY_HOME"
	nohup java -jar "$JETTY_HOME/start.jar" $OSKARI_OPTIONS >>"$LOGFILE" 2>&1 &
	echo "$!" > "$PIDFILE"
	;;
stop)
	[ -f "$PIDFILE" ] && kill `cat "$PIDFILE"`
	;;
status)
	[ -f "$PIDFILE" ] && ps -p `cat "$PIDFILE"` -f --no-headers
	;;
esac

exit 0
