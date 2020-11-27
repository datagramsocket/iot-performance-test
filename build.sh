#!/bin/bash

images=$(docker images | grep "infinovaiot/tb-ce-performance-test" |awk '{print $3}'| xargs)
if [ -n "$images" ]; then
  echo "remove old images:$images"
  docker rmi -f $images
fi
rm -f tb-ce-performance-test.tar
mvn clean install -DskipTests -Ddockerfile.skip=false
cd target/docker
docker build . -t infinovaiot/tb-ce-performance-test:latest
echo "save tb-ce-performance-test from codker..."
docker save infinovaiot/tb-ce-performance-test:latest -o ./../../tb-ce-performance-test.tar

docker tag infinovaiot/tb-ce-performance-test:latest 10.82.27.90/iot/infinovaiot/tb-ce-performance-test:latest
docker push 10.82.27.90/iot/infinovaiot/tb-ce-performance-test:latest

