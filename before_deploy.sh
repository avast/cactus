#!/bin/bash

original_json=`cat bintrayDeploy.json`

release_version=$TRAVIS_TAG
release_date=`date +"%Y-%m-%d"`

result_json="${original_json//VERSION/$release_version}"
result_json2="${result_json//DATE/$release_date}"

echo $result_json2 > bintrayDeploy.json