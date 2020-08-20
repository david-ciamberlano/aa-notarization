#!/bin/sh

export COMPOSE_FILE_PATH="${PWD}/target/classes/docker/docker-compose.yml"

if [ -z "${M2_HOME}" ]; then
  export MVN_EXEC="mvn"
else
  export MVN_EXEC="${M2_HOME}/bin/mvn"
fi

start() {
    docker volume create alf-algo-acs-volume
    docker volume create alf-algo-db-volume
    docker volume create alf-algo-ass-volume
    docker-compose -f "$COMPOSE_FILE_PATH" up --build -d
}

start_share() {
    docker-compose -f "$COMPOSE_FILE_PATH" up --build -d alf-algo-share
}

start_acs() {
    docker-compose -f "$COMPOSE_FILE_PATH" up --build -d alf-algo-acs
}

down() {
    if [ -f "$COMPOSE_FILE_PATH" ]; then
        docker-compose -f "$COMPOSE_FILE_PATH" down
    fi
}

purge() {
    docker volume rm -f alf-algo-acs-volume
    docker volume rm -f alf-algo-db-volume
    docker volume rm -f alf-algo-ass-volume
}

build() {
    $MVN_EXEC clean package
}

build_share() {
    docker-compose -f "$COMPOSE_FILE_PATH" kill alf-algo-share
    yes | docker-compose -f "$COMPOSE_FILE_PATH" rm -f alf-algo-share
    $MVN_EXEC clean package -pl alf-algo-share,alf-algo-share-docker
}

build_acs() {
    docker-compose -f "$COMPOSE_FILE_PATH" kill alf-algo-acs
    yes | docker-compose -f "$COMPOSE_FILE_PATH" rm -f alf-algo-acs
    $MVN_EXEC clean package -pl alf-algo-integration-tests,alf-algo-platform,alf-algo-platform-docker
}

tail() {
    docker-compose -f "$COMPOSE_FILE_PATH" logs -f
}

tail_all() {
    docker-compose -f "$COMPOSE_FILE_PATH" logs --tail="all"
}

prepare_test() {
    $MVN_EXEC verify -DskipTests=true -pl alf-algo-platform,alf-algo-integration-tests,alf-algo-platform-docker
}

test() {
    $MVN_EXEC verify -pl alf-algo-platform,alf-algo-integration-tests
}

case "$1" in
  build_start)
    down
    build
    start
    tail
    ;;
  build_start_it_supported)
    down
    build
    prepare_test
    start
    tail
    ;;
  start)
    start
    tail
    ;;
  stop)
    down
    ;;
  purge)
    down
    purge
    ;;
  tail)
    tail
    ;;
  reload_share)
    build_share
    start_share
    tail
    ;;
  reload_acs)
    build_acs
    start_acs
    tail
    ;;
  build_test)
    down
    build
    prepare_test
    start
    test
    tail_all
    down
    ;;
  test)
    test
    ;;
  *)
    echo "Usage: $0 {build_start|build_start_it_supported|start|stop|purge|tail|reload_share|reload_acs|build_test|test}"
esac