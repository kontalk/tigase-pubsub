#!/bin/bash
LIBS="/home/smoku/workspace/tigase-server/target /home/smoku/workspace/tigase-server/libs /home/smoku/workspace/tigase-pubsub/target /usr/share/tigase-server/lib"
for DIR in $LIBS; do CLASSPATH="`ls -d $DIR/*.jar 2>/dev/null | tr '\n' :`$CLASSPATH"; done
export CLASSPATH
#echo $CLASSPATH
exec ${0/%.sh/.groovy}
