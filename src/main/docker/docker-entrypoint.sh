#!/usr/bin/env bash

# Define locations of our container's property values
PROPERTIES=/etc/bucketeer/bucketeer.properties
PROPERTIES_TMPL=/etc/bucketeer/bucketeer.properties.tmpl
PROPERTIES_DEFAULT=/etc/bucketeer/bucketeer.properties.default

# Find the python application on our system
PYTHON2=$(which python2)

# Create properties file from defaults and environment
read -d '' SCRIPT <<- EOT
import os,string,ConfigParser,StringIO;
template=string.Template(open('$PROPERTIES_TMPL').read());
config = StringIO.StringIO()
config.write('[bucketeer]\n')
config.write(open('$PROPERTIES_DEFAULT').read())
config.seek(0, os.SEEK_SET)
config_parser = ConfigParser.ConfigParser()
config_parser.optionxform = str
config_parser.readfp(config)
properties = dict(config_parser.items('bucketeer'))
properties.update(os.environ)
print(template.safe_substitute(properties))
EOT

# Write our merged properties file to /etc directory
$PYTHON2 -c "$SCRIPT" >> $PROPERTIES

# If we have feature flags, grab the configuration
if [[ -v FEATURE_FLAGS && ! -z FEATURE_FLAGS ]]; then
  curl -s "${FEATURE_FLAGS}" > /etc/bucketeer/bucketeer-features.conf
  chown bucketeer /etc/bucketeer/bucketeer-features.conf
fi

# Replaces parent process so signals are processed correctly
exec "$@"
