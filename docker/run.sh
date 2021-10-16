#!/bin/sh

docker container run -ti --rm --name thoughts-clj -p 3000:3000 \
  -e HTTP_HOST=$HTTP_HOST \
  -e HTTP_PORT=$HTTP_PORT \
  -e HTTP_API_VERSION=$HTTP_API_VERSION \
  -e HTTP_API_PATH_PREFIX=$HTTP_API_PATH_PREFIX \
  -e HTTP_API_JWS_SECRET=$HTTP_API_JWS_SECRET \
  thoughts-clj