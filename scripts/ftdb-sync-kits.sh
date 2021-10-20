#!/bin/sh
#
# Synchro de la base ftdb depuis le site ft-datenbank (kits)
#
java -jar target/ftdb-sync-1.0.0-jar-with-dependencies.jar --user=ftdb --pwd=ftdb --cat=653 --parts
