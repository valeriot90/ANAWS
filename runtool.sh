#!/bin/sh
DEBUG=
DHCPPIDFILE=dhclient.pid
PORT=8787
case $1 in
	"")
		;;
	"-d")
		DEBUG="-Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,address=${PORT},server=y,suspend=y"
		;;
	*)
		echo "Usage: ./runtool.sh [ -d]"
		echo "		-d 	enable jvm options for remotely debugging the tool"
		;;
esac 

# Check runtime dependencies
if [ ! -d /etc/frr  -a ! -e /bin/vtysh ]; then
	echo "Frrouting daemon seems to be not installed. Exiting"
	exit 1
fi

if [ ! -e /etc/frr/ospfd.conf ]; then
	echo "Copying the ospfd configuration for this project to be run."
	echo "Requires root permissions"
	sudo cp ospfd.conf /etc/frr/
	echo "Restart services"
	sudo service frr restart || sudo systemctl restart frr.service || { echo "Error restarting services"; exit 1; }
	if [  $? == 1 ]; then
		exit 1
	fi
fi

# Check ospfd is running
if PID=$(pgrep ospfd)
then
    echo "ospfd is running"
else
    echo "ospfd is not running. Please run the service before starting the tool."
    exit 1
fi

# Make dirs and standard classes if there aren't any
if [ ! -d "classes" ]; then
	mkdir classes
	cd classes
	mkdir new
	mkdir std
	cd std
	touch WEB VOIP VIDEO EXCESS
	echo "class WEB" > WEB
	echo " bandwidth percent 5" >> WEB
	echo " queue-limit 20 packets" >> WEB
	echo "class VOIP" > VOIP
	echo " bandwidth percent 30" >> VOIP
	echo " queue-limit 100 packets" >> VOIP
	echo "class VIDEO" > VIDEO
	echo " bandwidth percent 60" >> VIDEO
	echo "class EXCESS" > EXCESS
	echo " bandwidth percent 4" >> EXCESS
	cd ..
	cd ..
fi

INTERFACE0="tap0"
if [ -h /sys/class/net/${INTERFACE0} ]; then
	echo "Waiting DHCP lease from interface ${INTERFACE0}"
	sudo dhclient -pf ${DHCPPIDFILE} ${INTERFACE0} || { echo "DHCP Error. Check connectivity"; exit 1; }
	if [ $? -ne 0 ]; then
		exit 1
	fi
else
	echo "${INTERFACE0} hasn't been found in this computer. Exiting"
	exit 1 
fi

LIBDIR=${LIBDIR:-../lib}
JAVACP=${LIBDIR}/bcprov-jdk15on-159.jar:
JAVACP=${JAVACP}${LIBDIR}/slf4j-api-1.7.9.jar:
JAVACP=${JAVACP}${LIBDIR}/eddsa-0.2.0.jar:
JAVACP=${JAVACP}${LIBDIR}/slf4j-jdk14-1.7.9.jar:
JAVACP=${JAVACP}${LIBDIR}/jzlib-1.1.3.jar:
JAVACP=${JAVACP}${LIBDIR}/sshj-0.23.0.jar:
JAVACP=${JAVACP}${LIBDIR}/expectit-core-0.9.0.jar:
JAVACP=${JAVACP}${LIBDIR}/expectit-core-0.9.0-source.jar:
JAVACP=${JAVACP}${LIBDIR}/expectit-core-0.9.0-javadoc.jar:
JAVACP=${JAVACP}.

cd bin/
java ${DEBUG} -cp ${JAVACP} Tool

# Clean up 
cd ../
echo "Releasing DHCP"
sudo dhclient -r ${INTERFACE0}
sudo kill -9 $( cat ${DHCPPIDFILE} )
rm -f ${DHCPPIDFILE}
