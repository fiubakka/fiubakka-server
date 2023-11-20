#!/bin/bash
su - postgres -c "pg_ctl -D /var/lib/postgresql/data start"
exec java -jar app.jar
