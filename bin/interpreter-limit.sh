#!/bin/bash

cd $ZEPPELIN_HOME

max_instances=10000

# Count processes containing 'interpreter/spark/spark-interpreter-'
current_instances=$(ps -ef | grep $ZEPPELIN_HOME'/interpreter/spark/spark-interpreter-' | grep -v 'grep' | wc -l )
echo "current interpreter instance : $current_instances"

if [ "$current_instances" -ge "$max_instances" ]; then
    echo "Interpreter instance limit reached ($max_instances)."
    exit 1
fi